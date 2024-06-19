package org.knime.product.rcp.intro;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.apache.http.client.utils.URIBuilder;
import org.eclipse.core.runtime.Platform;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.EclipseUtil;
import org.knime.core.util.HubStatistics;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.proxy.URLConnectionFactory;
import org.knime.product.rcp.KNIMEApplication;
import org.knime.product.rcp.intro.json.JSONCategory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class WelcomeAPEndpoint implements Supplier<Optional<JSONCategory[]>> {
    private static final String ENDPOINT = "https://tips-and-tricks.knime.com/welcome-ap";

    static Future<Optional<JSONCategory[]>> future;

    /**
     * @return
     */
    static Optional<JSONCategory[]> request() {
        if (EclipseUtil.isRunFromSDK()) {
            return Optional.empty();
        }
        try (final var suppression = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            var url = new URIBuilder(ENDPOINT) //
                .addParameter("knid", KNIMEConstants.getKNID()) //
                .addParameter("version", KNIMEConstants.VERSION) //
                .addParameter("os", Platform.getOS()) //
                .addParameter("osname", KNIMEConstants.getOSVariant()) //
                .addParameter("arch", Platform.getOSArch()) //
                .addParameter("details", buildAPUsage() + "," + buildHubUsage()) //
                .addParameter("ui", "modern").build().toURL();
            var connection = (HttpURLConnection)URLConnectionFactory.getConnection(url);
            connection.setReadTimeout(5000);
            connection.setConnectTimeout(2000);
            connection.connect();
            try (var response = connection.getInputStream()) {
                return Optional.ofNullable(parseResponse(response));
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            NodeLogger.getLogger(ProductHints.class).debug("Could not call 'welcome-AP' endpoint: " + e.getMessage(),
                e);
        }
        return Optional.empty();
    }

    private static JSONCategory[] parseResponse(final InputStream response) throws IOException {
        var mapper = new ObjectMapper();
        var currentThreadClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ProductHints.class.getClassLoader());
        try {
            return mapper.readValue(response, JSONCategory[].class);
        } finally {
            Thread.currentThread().setContextClassLoader(currentThreadClassLoader);
        }
    }

    private static String buildAPUsage() {
        // simple distinction between first and recurring users
        var apUsage = "apUsage:";
        if (KNIMEApplication.isStartedWithFreshWorkspace()) {
            apUsage += "first";
        } else {
            apUsage += "recurring";
        }
        return apUsage;
    }

    private static String buildHubUsage() {
        var hubUsage = "hubUsage:";
        Optional<ZonedDateTime> lastLogin = Optional.empty();
        Optional<ZonedDateTime> lastUpload = Optional.empty();
        try {
            lastLogin = HubStatistics.getLastLogin();
            lastUpload = HubStatistics.getLastUpload();
        } catch (Exception e) { // NOSONAR
            NodeLogger.getLogger(ProductHints.class).info("Hub statistics could not be fetched: " + e.getMessage(), e);
        }

        if (lastUpload.isPresent()) {
            hubUsage += "contributer";
        } else if (lastLogin.isPresent()) {
            hubUsage += "user";
        } else {
            hubUsage += "none";
        }
        return hubUsage;
    }

    @Override
    public Optional<JSONCategory[]> get() {
        if (future == null) {
            future = CompletableFuture.supplyAsync(WelcomeAPEndpoint::request);
        }
        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            NodeLogger.getLogger(WelcomeAPEndpoint.class).debug(e.getMessage());
            return Optional.empty();
        }
    }
}
