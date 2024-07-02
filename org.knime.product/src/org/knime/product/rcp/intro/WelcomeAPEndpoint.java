package org.knime.product.rcp.intro;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.http.client.utils.URIBuilder;
import org.eclipse.core.runtime.Platform;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.EclipseUtil;
import org.knime.core.util.HubStatistics;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.proxy.URLConnectionFactory;
import org.knime.product.rcp.intro.json.JSONCategory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Interact with the "Welcome AP" endpoint.
 */
public final class WelcomeAPEndpoint {
    private static final String ENDPOINT = "https://tips-and-tricks.knime.com/welcome-ap";

    private static WelcomeAPEndpoint instance;

    private Future<Optional<JSONCategory[]>> m_future;

    /**
     * Singleton to be accessible from classic UI or early lifecycle stages of web UI.
     * @return the singleton instance
     */
    public static WelcomeAPEndpoint getInstance() {
        if (instance == null) {
            instance = new WelcomeAPEndpoint();
        }
        return instance;
    }

    private WelcomeAPEndpoint() {
        // singleton
    }

    /**
     *
     * @param calledFromWebUI Whether the call is made from the Web UI.
     */
    public void callEndpointForTracking(final boolean calledFromWebUI) {
        getCategories(calledFromWebUI, null);
    }

    /**
     * @apiNote This method call might block until the page content is retrieved.
     * @param calledFromWebUI Whether the call is made from the Web UI.
     * @param companyName Customisation information from AP instance, nullable. Already URL-encoded.
     * @return the home/welcome page content categories or an empty optional if run from the SDK
     */
    public Optional<JSONCategory[]> getCategories(final boolean calledFromWebUI, final String companyName) {
        if (m_future == null) {
            m_future = CompletableFuture.supplyAsync(() -> WelcomeAPEndpoint.request(calledFromWebUI, companyName));
        }
        try {
            return m_future.get();
        } catch (InterruptedException | ExecutionException e) {
            return Optional.empty();
        }
    }

    /**
     * Perform request to endpoint.
     *
     * @param calledFromWebUI Whether the call is made from the Web UI.
     * @param companyName Customisation information from AP instance, nullable. Already URL-encoded.
     * @return The home page tile contents, grouped into categories.
     */
    private static Optional<JSONCategory[]> request(final boolean calledFromWebUI, final String companyName) {
        if (EclipseUtil.isRunFromSDK()) {
            return Optional.empty();
        }
        try (final var suppression = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            var urlBuilder = new URIBuilder(ENDPOINT) //
                .addParameter("knid", KNIMEConstants.getKNID()) //
                .addParameter("version", KNIMEConstants.VERSION) //
                .addParameter("os", Platform.getOS()) //
                .addParameter("osname", KNIMEConstants.getOSVariant()) //
                .addParameter("arch", Platform.getOSArch()) //
                .addParameter("details", buildAPUsage() + "," + HubUsage.requestParameter());
            if (calledFromWebUI) {
                urlBuilder.addParameter("ui", "modern");
            }
            if (companyName != null) {
                urlBuilder.addParameter("brand", companyName);
            }
            var url = urlBuilder.build().toURL();
            var connection = (HttpURLConnection)URLConnectionFactory.getConnection(url);
            connection.setReadTimeout(5000);
            connection.setConnectTimeout(2000);
            connection.connect();
            try (var response = connection.getInputStream()) {
                // these dates have now been reported to instrumentation via the request
                HubStatistics.getLastLogin().ifPresent(
                    ld -> HubStatistics.storeKnimeHubStat(HubStatistics.LAST_SENT_KNIME_HUB_LOGIN, ld.toString()));
                HubStatistics.getLastUpload().ifPresent(
                    ud -> HubStatistics.storeKnimeHubStat(HubStatistics.LAST_SENT_KNIME_HUB_UPLOAD, ud.toString()));

                return Optional.of(parseResponse(response));
            } finally {
                connection.disconnect();
            }
        } catch (URISyntaxException | IOException e) {
            // URISyntaxException should never happen -- URL is hardcoded
            NodeLogger.getLogger(WelcomeAPEndpoint.class).error("Calling welcome page endpoint failed", e);
            return Optional.empty();
        }
    }

    private static JSONCategory[] parseResponse(final InputStream response) throws IOException {
        var mapper = new ObjectMapper();
        var currentThreadClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(WelcomeAPEndpoint.class.getClassLoader());
        try {
            return mapper.readValue(response, JSONCategory[].class);
        } finally {
            Thread.currentThread().setContextClassLoader(currentThreadClassLoader);
        }
    }

    private static String buildAPUsage() {
        // simple distinction between first and recurring users
        var apUsage = "apUsage:";
        if (KNIMEConstants.isUIDNew()){
            apUsage += "first";
        } else {
            apUsage += "recurring";
        }
        return apUsage;
    }

    enum HubUsage {
            /**
             * No interaction with any KNIME hub during the previous session (from selecting workspace to switching
             * workspace or shutting down AP) in the current workspace
             */
            NONE("none"),
            /** A login to a KNIME hub has happened during the last time using the current workspace */
            USER("user"),
            /** An upload to a KNIME hub has happened during the last time using the current workspace */
            CONTRIBUTOR("contributer");

        final String id;

        HubUsage(final String identifier) {
            id = identifier;
        }

        static HubUsage current() {
            Optional<ZonedDateTime> lastLogin = Optional.empty();
            Optional<ZonedDateTime> lastUpload = Optional.empty();
            Optional<ZonedDateTime> lastSentLogin = Optional.empty();
            Optional<ZonedDateTime> lastSentUpload = Optional.empty();

            try {
                lastLogin = HubStatistics.getLastLogin();
                lastUpload = HubStatistics.getLastUpload();
                lastSentLogin = HubStatistics.getLastSentLogin();
                lastSentUpload = HubStatistics.getLastSentUpload();
            } catch (Exception e) { // NOSONAR
                NodeLogger.getLogger(WelcomeAPEndpoint.class)
                    .info("Hub statistics could not be fetched: " + e.getMessage(), e);
            }

            if (lastUpload.isPresent() && !lastUpload.equals(lastSentUpload)) {
                return CONTRIBUTOR;
            } else if (lastLogin.isPresent() && !lastLogin.equals(lastSentLogin)) {
                return USER;
            }

            return NONE;
        }

        static String requestParameter() {
            return "hubUsage:" + HubUsage.current().id;
        }
    }

}
