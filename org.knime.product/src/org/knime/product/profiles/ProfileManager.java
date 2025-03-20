/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   31.01.2018 (thor): created
 */
package org.knime.product.profiles;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.stream.Streams;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.core.internal.preferences.DefaultPreferences;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.KNIMEServerHostnameVerifier;
import org.knime.core.util.Pair;
import org.knime.core.util.PathUtils;
import org.knime.core.util.proxy.URLConnectionFactory;
import org.osgi.framework.FrameworkUtil;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import jakarta.ws.rs.core.HttpHeaders;

/**
 * Manager for profiles that should be applied during startup. This includes custom default preferences and
 * supplementary files such as database drivers. The profiles must be applied as early as possible during startup,
 * ideally as the first command in the application's start method.
 *
 * @author Thorsten Meinl, KNIME AG, Zurich, Switzerland
 * @author Leon Wenzler, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
public class ProfileManager {

    private static final ProfileManager INSTANCE = new ProfileManager();


    private static final String ORIGIN_HEADERS_FILE = ".originHeaders";

    private static final Pattern DOUBLE_DOLLAR_PATTERN = Pattern.compile("\\$(\\$\\{[^:\\}]+:[^\\}]+\\})");

    // Does not exist in `jakarta.ws.rs.core.MediaType`. Package scope for tests.
    static final String PREFERENCES_MEDIA_TYPE = "application/zip";

    private final List<Runnable> m_collectedLogs = new ArrayList<>(2);

    private final List<Profile> m_appliedProfiles = new LinkedList<>();

    /**
     * Returns the singleton instance.
     *
     * @return the singleton, never <code>null</code>
     */
    public static ProfileManager getInstance() {
        return INSTANCE;
    }

    private final ProfileResolver m_profileResolver;

    private ProfileManager() {
        // potential providers, increasing in priority (next overwrites previous)
        List<Supplier<IProfileProvider>> providers = getExtensionPointProviderSuppliers();
        providers.add(WorkspaceProfileProvider::new);
        providers.add(CommandlineProfileProvider::new);
        m_profileResolver = new ProfileResolver(providers);
    }

    private List<Supplier<IProfileProvider>> getExtensionPointProviderSuppliers() {
        // maps from an extension to a creator of an profile provider
        Function<IConfigurationElement, Supplier<IProfileProvider>> mapper = extension -> () -> {
            try {
                return (IProfileProvider)extension.createExecutableExtension("class");
            } catch (CoreException ex) {
                m_collectedLogs.add(() -> NodeLogger.getLogger(ProfileManager.class).error( //
                    "Could not create profile provider instance from class " //
                        + extension.getAttribute("class") + ". No profiles will be processed.", ex));
                return new EmptyProfileProvider();
            }
        };

        IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint("org.knime.product.profileProvider");
        return Stream.of(point.getExtensions()) //
            .flatMap(ext -> Stream.of(ext.getConfigurationElements())) //
            .map(mapper) //
            .collect(Collectors.toList()); // NOSONAR, we want to add other providers after collecting extensions
    }

    /**
     * Apply the available profiles to this instance. This includes setting new default preferences and copying
     * supplementary files to instance's configuration area.
     */
    public void applyProfiles() {
        applyProfiles(false);
    }

    void applyProfiles(final boolean overwrite) {
        List<Profile> localProfiles = Streams.of(m_profileResolver.iterator()) //
            // Flatten all profiles from different providers into one stream.
            .flatMap(Function.identity()).toList();
        try {
            applyPreferences(localProfiles, overwrite);
        } catch (IOException | NoSuchFieldException | SecurityException | IllegalArgumentException
                | IllegalAccessException ex) {
            m_collectedLogs.add(() -> NodeLogger.getLogger(ProfileManager.class)
                .error("Could not apply preferences from profiles: " + ex.getMessage(), ex));
        }

        m_collectedLogs.stream().forEach(r -> r.run());
    }

    private void applyPreferences(final List<Profile> profiles, final boolean overwrite)
        throws IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {

        // This field was made private in a recent eclipse upgrade so we need to use reflection ot access it
        final var pluginCustomizationFileField = DefaultPreferences.class.getDeclaredField("pluginCustomizationFile");
        pluginCustomizationFileField.setAccessible(true);

        if (!overwrite && (String)pluginCustomizationFileField.get(null) != null) {
            return; // plugin customizations are already explicitly provided by someone else
        }
        m_appliedProfiles.clear();

        final var combinedProperties = new Properties();
        for (var profile : profiles) {
            m_appliedProfiles.add(profile);
            try (var stream = Files.walk(profile.localPath())) {
                List<Path> prefFiles = stream //
                    .filter(f -> Files.isRegularFile(f) && f.toString().endsWith(".epf")) //
                    .sorted().toList();

                final var props = new Properties();
                for (Path f : prefFiles) {
                    loadProperties(f, props);
                }
                replaceVariables(props, profile);
                combinedProperties.putAll(props);
            }
            m_collectedLogs.add(() -> NodeLogger.getLogger(ProfileManager.class).debug(String.format( //
                "Applied profile \"%s\" from %s", profile.name(), profile.provider().getProfilesLocation())));
        }

        // remove "/instance" prefixes from preferences because otherwise they are not applied as default preferences
        // (because they are instance preferences...)
        for (var key : new HashSet<>(combinedProperties.keySet())) {
            if (key.toString().startsWith("/instance/")) {
                Object value = combinedProperties.remove(key);
                combinedProperties.put(key.toString().substring("/instance/".length()), value);
            }
        }

        var pluginCustFile = getStateLocation().resolve("combined-preferences.epf");
        if (Files.exists(pluginCustFile) && !Files.isWritable(pluginCustFile)) {
            final var tempCustFile = PathUtils.createTempFile("combined-preferences", ".epf");
            final var nonWorkingFile = pluginCustFile;
            pluginCustFile = tempCustFile;

            m_collectedLogs
                .add(() -> NodeLogger.getLogger(ProfileManager.class).warn("Could not write combined preferences file '"
                    + nonWorkingFile + "', will use temporary file '" + tempCustFile + "' instead."));
        }

        // It's important here to write to a stream and not a reader because when reading the file back in
        // org.eclipse.core.internal.preferences.DefaultPreferences.loadProperties(String) also reads from a stream
        // and therefore assumes it's ISO-8859-1 encoded (with replacement for UTF characters).
        try (var out = Files.newOutputStream(pluginCustFile)) {
            combinedProperties.store(out, "");
        }
        pluginCustomizationFileField.set(null, pluginCustFile.toAbsolutePath().toString());
    }

    private void replaceVariables(final Properties props, final Profile profile) throws IOException {
        final var profileLocation = profile.localPath();
        final var originFile = profileLocation.getParent().resolve(ORIGIN_HEADERS_FILE);
        List<VariableReplacer> replacers = Arrays.asList( //
            new VariableReplacer.EnvVariableReplacer(m_collectedLogs),
            new VariableReplacer.SyspropVariableReplacer(m_collectedLogs),
            new VariableReplacer.ProfileVariableReplacer(profileLocation, m_collectedLogs),
            new VariableReplacer.OriginVariableReplacer(originFile, m_collectedLogs),
            new VariableReplacer.CustomVariableReplacer(profile.provider(), m_collectedLogs));

        for (var key : props.stringPropertyNames()) {
            var value = props.getProperty(key);

            for (VariableReplacer rep : replacers) {
                value = rep.replaceVariables(value);
            }

            // finally replace escaped "variables" so that the double dollars are removed, e.g.:
            //     /instance/org.knime.product/non-variable=bla/$${custom:var}/foo
            // becomes
            //     /instance/org.knime.product/non-variable=bla/${custom:var}/foo
            props.replace(key, DOUBLE_DOLLAR_PATTERN.matcher(value).replaceAll("$1"));
        }
    }

    private static void loadProperties(final Path path, final Properties props) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
    }

    private static boolean isLocalProfile(final URI profileLocation) {
        return "file".equalsIgnoreCase(profileLocation.getScheme());
    }

    private static boolean isRemoteProfile(final URI profileLocation) {
        return profileLocation.getScheme().startsWith("http");
    }

    private static Path getStateLocation() {
        final var myself = FrameworkUtil.getBundle(ProfileManager.class);
        return Platform.getStateLocation(myself).toFile().toPath();
    }

    /**
     * The path to the local profiles of the last (highest priority) {@link IProfileProvider}
     * that the resolver used. Use {@link ProfileManager#getLocalProfilesLocation(IProfileProvider)}
     * for more control over which profile provider you want the local path from.
     *
     * @return the local profiles directory or an empty optional if no profiles available
     */
    public Optional<Path> getLocalProfilesLocation() {
        return getLocalProfilesLocation(m_profileResolver.m_currentProvider);
    }

    /**
     * The path to the local profiles directory (either a local profiles folder or
     * download and 'cached' profiles from a remote location).
     *
     * @param provider the source of the profiles
     * @return the local profiles directory or an empty optional if no profiles available
     * @since 5.5
     */
    public static Optional<Path> getLocalProfilesLocation(final IProfileProvider provider) {
        if (provider == null) {
            return Optional.empty();
        }
        URI profileLocation = provider.getProfilesLocation();
        if (profileLocation == null) {
            return Optional.empty();
        }
        if (isLocalProfile(profileLocation)) {
            return Optional.of(Paths.get(profileLocation));
        } else if (isRemoteProfile(profileLocation)) {
            return Optional.of(getStateLocation().resolve(provider.getClass().getName()));
        } else {
            throw new IllegalArgumentException("Profiles from '" + profileLocation.getScheme() + " are not supported");
        }
    }

    /**
     * Returns the list of requested profiles. See also {@link IProfileProvider#getRequestedProfiles()}.
     *
     * @return the list, never <code>null</code> but can be empty
     */
    public List<String> getRequestProfiles() {
        return m_profileResolver.m_providers.stream() //
            .map(Supplier::get) //
            .flatMap(p -> p.getRequestedProfiles().stream()).toList();
    }

    /**
     * Returns the list of applied profiles. The profile records returned by this method
     * take into account the validity of the {@link Profile} (e.g. existing and non-empty),
     * and whether or not profiles have not been overwritten from a previous run.
     *
     * @return the list, never <code>null</code> but can be empty
     * @since 5.5
     */
    public List<Profile> getAppliedProfiles() {
        return Collections.unmodifiableList(m_appliedProfiles);
    }

    /**
     * Returns whether profiles have been successfully downloaded from the remote location. If {@link #applyProfiles()}
     * hasn't been called yet or the profile source is not a remote server, an empty optional will be returned.
     *
     * @return <code>true</code> if profile download was successful, <code>false</code> if it failed, or an empty
     *         optional
     */
    public Optional<Boolean> downloadWasSuccessful() {
        return Optional.ofNullable(m_profileResolver.m_downloadSuccessful);
    }

    /**
     * A downloading {@link Iterator} for applying profiles from multiple {@link IProfileProvider}.
     * Iteratively downloads the profiles from each provider, given that it specifies a non-zero amount
     * of profile names and a valid profile location.
     * <p>
     * The {@link #iterator()} method provides streamed batches of profiles, each containing profiles
     * from only one (the {@link #m_currentProvider}) profile provider. Skips and logs failed downloads.
     * </p>
     *
     * @author Leon Wenzler, KNIME GmbH, Konstanz, Germany
     * @since 5.5
     */
    private final class ProfileResolver implements Iterable<Stream<Profile>> {

        private final List<Supplier<IProfileProvider>> m_providers;

        private IProfileProvider m_currentProvider;

        private Boolean m_downloadSuccessful;

        public ProfileResolver(final List<Supplier<IProfileProvider>> providers) {
            m_providers = providers.stream().map(Suppliers::memoize).toList();
        }

        @Override
        public Iterator<Stream<Profile>> iterator() {
            return new Iterator<Stream<Profile>>() { // NOSONAR, only needed once

                private final Iterator<Supplier<IProfileProvider>> m_inner = m_providers.iterator();

                private Path m_currentPath;

                @Override
                public Stream<Profile> next() {
                    if (m_currentPath == null) {
                        throw new NoSuchElementException("No profiles are present in the current provider");
                    }
                    final var basePath = m_currentPath;
                    m_currentPath = null;
                    return Profile.stream(m_currentProvider, basePath);
                }

                @Override
                public boolean hasNext() {
                    while (m_currentPath == null && m_inner.hasNext()) {
                        m_currentProvider = m_inner.next().get();
                        if (m_currentProvider.getRequestedProfiles().isEmpty()) {
                            continue;
                        }
                        m_currentPath = fetchNext();
                    }
                    return m_currentPath != null;
                }

                @SuppressWarnings("resource")
                private Path fetchNext() throws IllegalArgumentException {
                    final var profileLocation = m_currentProvider.getProfilesLocation();
                    if (isLocalProfile(profileLocation)) {
                        return Paths.get(profileLocation);
                    } else if (isRemoteProfile(profileLocation)) {
                        final var result = createHttpRequest(m_currentProvider, m_collectedLogs);
                        if (result != null) {
                            return download(m_currentProvider, result.getFirst(), result.getSecond(), m_collectedLogs);
                        }
                    } else {
                        final var scheme = profileLocation.getScheme();
                        throw new IllegalArgumentException("Profiles from '" + scheme + "' are not supported");
                    }
                    return null;
                }
            };
        }

        private Path download(final IProfileProvider provider, final CloseableHttpClient client,
            final HttpUriRequest request, final List<Runnable> logs) {
            final var stateDir = getStateLocation();
            final var profileDir = stateDir.resolve(provider.getClass().getName());

            try (client) {
                // compute list of profiles that are requested but not present locally yet
                List<String> newRequestedProfiles = new ArrayList<>(provider.getRequestedProfiles());
                if (Files.isDirectory(profileDir)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(profileDir, Files::isDirectory)) {
                        stream.forEach(p -> newRequestedProfiles.remove(p.getFileName().toString()));
                    }
                }
                Files.createDirectories(stateDir);
                if (newRequestedProfiles.isEmpty() && Files.isDirectory(profileDir)) {
                    // if new profiles are requested we must not make a conditional request
                    final var lastModified = Files.getLastModifiedTime(profileDir).toInstant();
                    request.setHeader("If-Modified-Since",
                        DateTimeFormatter.RFC_1123_DATE_TIME.format(lastModified.atZone(ZoneId.of("GMT"))));
                }

                try (var response = client.execute(request)) {
                    processHttpResponse(provider, response);
                    // if it was null (uninitialized) set `true`, otherwise keep previous status
                    m_downloadSuccessful = m_downloadSuccessful == null || m_downloadSuccessful;
                }
            } catch (IOException ex) {
                m_downloadSuccessful = false;
                String msg = "Could not download profiles from " + provider.getProfilesLocation() + ": "
                    + ex.getMessage() + ". " + (Files.isDirectory(profileDir)
                        ? "Will use existing but potentially outdated profiles." : "No profiles will be applied.");
                logs.add(() -> NodeLogger.getLogger(ProfileManager.class).error(msg, ex));
                return null;
            }

            return profileDir;
        }

        // -- REQUEST PREPARATION & RESPONSE HANDLING --

        @SuppressWarnings("resource")
        private static Pair<CloseableHttpClient, HttpUriRequest> createHttpRequest(final IProfileProvider provider,
            final List<Runnable> logs) {
            try {
                final var builder = new URIBuilder(provider.getProfilesLocation());
                builder.addParameter("profiles", String.join(",", provider.getRequestedProfiles()));
                final var profileUri = builder.build();

                logs.add(
                    () -> NodeLogger.getLogger(ProfileManager.class).info("Downloading profiles from " + profileUri));

                // proxy and timeout configuration
                final var proxy = ProxySelector.getDefault().select(profileUri).stream() //
                    .filter(p -> p != null && p.address() != null) //
                    .findFirst().map(p -> ((InetSocketAddress)p.address())) //
                    .map(p -> new HttpHost(p.getHostString(), p.getPort())) //
                    .orElse(null);
                final var requestConfig = RequestConfig.custom() //
                    .setConnectTimeout(URLConnectionFactory.getDefaultURLConnectTimeoutMillis()) //
                    .setConnectionRequestTimeout(URLConnectionFactory.getDefaultURLConnectTimeoutMillis()) //
                    .setSocketTimeout(URLConnectionFactory.getDefaultURLReadTimeoutMillis()) //
                    .setProxy(proxy) //
                    .build();

                // creating the client for provider target
                final var client = HttpClients.custom() //
                    .setDefaultRequestConfig(requestConfig) //
                    .setSSLHostnameVerifier(KNIMEServerHostnameVerifier.getInstance()) //
                    .setRedirectStrategy(new DefaultRedirectStrategy()) //
                    .build();

                return Pair.create(client, new HttpGet(profileUri));
            } catch (URISyntaxException ex) {
                String msg = "Could not create HTTP client for downloading profiles from "
                    + provider.getProfilesLocation() + ": " + ex.getMessage();
                logs.add(() -> NodeLogger.getLogger(ProfileManager.class).error(msg, ex));
                return null;
            }
        }

        private static void processHttpResponse(final IProfileProvider provider, final CloseableHttpResponse response)
            throws IOException {
            final var profileDir = getStateLocation().resolve(provider.getClass().getName());

            int code = response.getStatusLine().getStatusCode();
            if ((code >= 200) && (code < 300)) {
                final var ct = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
                if ((ct == null) || (ct.getValue() == null) || !ct.getValue().startsWith(PREFERENCES_MEDIA_TYPE)) {
                    // this is a workaround because ZipInputStream doesn't complain when the read contents are
                    // no zip file - it just processes an empty zip
                    throw new IOException("Server did not return a ZIP file containing the selected profiles");
                }
                writePreferencesProfiles(response.getEntity(), profileDir);
                writeOriginHeaders(response.getAllHeaders(), profileDir);
            } else if (code == 304) { // 304 = Not Modified
                writeOriginHeaders(response.getAllHeaders(), profileDir);
            } else {
                throw new IOException(extractHttpError(response));
            }
        }

        private static void writePreferencesProfiles(final HttpEntity body, final Path profileDir) throws IOException {
            final var stateDir = getStateLocation();
            final var tempFile = PathUtils.createTempFile("profile-download", ".zip");
            try (var os = Files.newOutputStream(tempFile); var content = body.getContent()) {
                IOUtils.copyLarge(content, os);
            }

            final var tempDir = PathUtils.createTempDir("profile-download", stateDir);
            try (var zf = ZipFile.builder().setPath(tempFile).get()) {
                PathUtils.unzip(zf, tempDir);
            }

            // replace profiles only if new data has been downloaded successfully
            PathUtils.deleteDirectoryIfExists(profileDir);
            Files.move(tempDir, profileDir, StandardCopyOption.ATOMIC_MOVE);
            Files.delete(tempFile);
        }

        private static void writeOriginHeaders(final Header[] allHeaders, final Path profileDir) throws IOException {
            final var originHeadersCache = profileDir.resolve(ORIGIN_HEADERS_FILE);
            final var props = new Properties();
            for (var h : allHeaders) {
                props.put(h.getName(), h.getValue());
            }
            try (var os = Files.newOutputStream(originHeadersCache)) {
                props.store(os, "");
            }
        }

        private static String extractHttpError(final CloseableHttpResponse response)
            throws IOException, UnsupportedOperationException {
            // (1) If a body was sent with an error status, use body content as error message.
            final var body = response.getEntity();
            if ((body != null) && (body.getContentType() != null) && (body.getContentType().getValue() != null)
                && body.getContentType().getValue().startsWith("text/")) {
                final var buf = new byte[Math.min(4096, Math.max(4096, (int)body.getContentLength()))];
                try (var content = body.getContent()) {
                    return new String(buf, 0, content.read(buf), StandardCharsets.US_ASCII).trim();
                }
            }
            // (2) If the status itself contains a reason use that.
            if (!response.getStatusLine().getReasonPhrase().isEmpty()) {
                return response.getStatusLine().getReasonPhrase();
            }
            // (3) Otherwise, default to printing the error status code.
            return "Server returned status " + response.getStatusLine().getStatusCode();
        }
    }

    /**
     * Triple uniquely identifying a locally-resolved profile containing a type of {@link Properties},
     * resolving to local "Eclipse Preferences" (i.e. *.epf files).
     *
     * @param name The name of the Eclipse preference profile.
     * @param provider The source where it was specified to use this profile.
     * @param localPath The local path where this profile resides or was downloaded to.
     *
     * @author Leon Wenzler, KNIME GmbH, Konstanz, Germany
     * @since 5.5
     */
    public static record Profile(String name, IProfileProvider provider, Path localPath) {

        /**
         * Given an {@link IProfileProvider} containing a list of profile names and a base {@link Path}
         * where its contents reside, streams each valid {@link Profile} contained at that path.
         *
         * @param provider The source of profiles.
         * @param basePath The local path where all profiles of this provider reside.
         * @return {@link Stream} of valid preference {@link Profile}s from this provider.
         */
        public static Stream<Profile> stream(final IProfileProvider provider, final Path basePath) {
            final var basePathNormalized = basePath.normalize();

            return provider.getRequestedProfiles().stream() //
                // Map each profile location to multiple local profiles.
                .map(name -> new Profile(name, provider, basePath.resolve(name).normalize())) //
                .filter(p -> Files.isDirectory(p.localPath())) //
                // Remove profiles that are outside the profile root (e.g. with "../" in their name).
                // Use normalized profile root s.t. the `startsWith` check considers the real paths.
                .filter(p -> p.localPath().startsWith(basePathNormalized));
        }
    }
}
