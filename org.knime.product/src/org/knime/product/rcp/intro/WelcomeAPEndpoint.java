/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.org; Email: contact@knime.org
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
 */
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
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.eclipse.core.runtime.Platform;
import org.knime.core.internal.CorePlugin;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.EclipseUtil;
import org.knime.core.util.HubStatistics;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.User;
import org.knime.core.util.proxy.URLConnectionFactory;
import org.knime.product.rcp.KNIMEApplication;
import org.knime.product.rcp.intro.json.JSONCategory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Interact with the "Welcome AP" endpoint.
 */
public final class WelcomeAPEndpoint {

    private static final String KNIME_COM_ENDPOINT = "https://tips-and-tricks.knime.com/welcome-ap";

    private static WelcomeAPEndpoint instance;

    private Future<Optional<JSONCategory[]>> m_future;

    /**
     * Singleton to be accessible from classic UI or early lifecycle stages of web UI.
     *
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
            m_future =
                CompletableFuture.supplyAsync(() -> WelcomeAPEndpoint.requestCategories(calledFromWebUI, companyName));
        }
        try {
            return m_future.get();
        } catch (InterruptedException | ExecutionException e) { // NOSONAR (exception handled in requestCategories)
            return Optional.empty();
        }
    }

    /**
     * Negotiate which endpoints to hit and of which to return results from.
     *
     * @param calledFromWebUI Whether the call is made from the Web UI.
     * @param companyName Customisation information from AP instance, nullable. Already URL-encoded.
     * @return The home page tile contents, grouped into categories.
     */
    private static Optional<JSONCategory[]> requestCategories(final boolean calledFromWebUI, final String companyName) {
        if (EclipseUtil.isRunFromSDK()) {
            return Optional.empty();
        }
        var uiCustomization = CorePlugin.getInstance().getCustomizationService() //
            .map(s -> s.getCustomization().ui()).orElse(null);
        if (uiCustomization == null) {
            NodeLogger.getLogger(WelcomeAPEndpoint.class).info("No UI customizations available");
            return Optional.empty();
        }

        // always request KNIME endpoint for tracking, even though we might not use the response
        var responseFromKnimeEndpoint = performRequest(KNIME_COM_ENDPOINT, calledFromWebUI, companyName); // NOSONAR

        if (uiCustomization.isHideWelcomeAPTiles()) {
            return Optional.empty();
        }

        var responseFromCustomEndpoint = uiCustomization.getWelcomeAPEndpointURL() //
            .map(WelcomeAPEndpoint::replaceUserFieldInEndpointURLIfPresent) //
            .flatMap(endpoint -> performRequest(endpoint, calledFromWebUI, companyName));
        return responseFromCustomEndpoint.isPresent() ? responseFromCustomEndpoint : responseFromKnimeEndpoint;
    }

    private static Optional<JSONCategory[]> performRequest(final String endpointUrl, final boolean calledFromWebUI,
        final String companyName) {
        try (final var suppression = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            var urlBuilder = new URIBuilder(endpointUrl) //
                .addParameter("knid", KNIMEConstants.getKNID()) //
                .addParameter("version", KNIMEConstants.VERSION) //
                .addParameter("os", Platform.getOS()) //
                .addParameter("osname", KNIMEConstants.getOSVariant()) //
                .addParameter("arch", Platform.getOSArch()) //
                .addParameter("details", buildAPUsage() + "," + HubUsage.requestParameter(HubUsage.Scope.COMMUNITY)
                    + "," + HubUsage.requestParameter(HubUsage.Scope.NON_COMMUNITY));
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
                if (KNIME_COM_ENDPOINT.equals(endpointUrl)) {
                    HubUsage.dataSent();
                }
                return Optional.of(parseResponse(response));
            } finally {
                connection.disconnect();
            }
        } catch (URISyntaxException | IOException e) {
            NodeLogger.getLogger(WelcomeAPEndpoint.class)
                .error(String.format("Calling welcome page endpoint failed%s:%s",
                    KNIME_COM_ENDPOINT.equals(endpointUrl) ? "" : (" (" + endpointUrl + ")"), e.getMessage()), e);
            return Optional.empty();
        }
    }

    /**
     * For user-defined endpoints, replace the placeholder "{user}" with the actual user name. In most cases (99.9%+)
     * this method does nothing as the default endpoint in the public KNIME distribution is used (see
     * {@link #KNIME_COM_ENDPOINT}). Custom Business-Hubs might deliver AP customizations with custom endpoints having
     * place holders for the user name. The user name is determined by {@link User#getUsername()}.
     *
     * @return The modified endpoint URL in case it contains the placeholder "{user}". Otherwise the input is returned.
     */
    private static String replaceUserFieldInEndpointURLIfPresent(final String rawEndpointURLAsString) {
        if (rawEndpointURLAsString.contains("{user}")) {
            String userid;
            try {
                userid = User.getUsername();
            } catch (Exception ex) {
                NodeLogger.getLogger(WelcomeAPEndpoint.class).warn("Could not determine user name", ex);
                userid = System.getProperty("user.name");
            }
            return StringUtils.replace(rawEndpointURLAsString, "{user}", userid);
        }
        return rawEndpointURLAsString;
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
        if (KNIMEConstants.isUIDNew()) {
            apUsage += "first";
        } else {
            apUsage += "recurring";
        }
        return apUsage;
    }

    enum HubUsage {
            /**
             * AP was started in a new workspace, so while technically interaction with any Hub was {@link #NONE}, we
             * still want to differentiate the case where there was no previous session because the workspace is fresh.
             */
            NEW("missing"),
            /**
             * No interaction with any KNIME hub during the previous session (from selecting workspace to switching
             * workspace or shutting down AP) in the current workspace
             */
            NONE("none"),
            /** A login to a KNIME hub has happened during the last time using the current workspace */
            USER("user"),
            /** An upload to a KNIME hub has happened during the last time using the current workspace */
            CONTRIBUTOR("contributer");

        enum Scope {
                /** Compute the hub usage for the KNIME Community Hub. */
                COMMUNITY("hubUsage", //
                    HubStatistics::getLastLogin, HubStatistics::getLastUpload, //
                    HubStatistics::getLastSentLogin, HubStatistics::getLastSentUpload),
                /** Compute the hub usage for all other KNIME Hub instances. */
                NON_COMMUNITY("bhubUsage", //
                    HubStatistics::getLastNonCommunityLogin, HubStatistics::getLastNonCommunityUpload, //
                    HubStatistics::getLastSentNonCommunityLogin, HubStatistics::getLastSentNonCommunityUpload);

            final String m_parameterName;

            final Supplier<Optional<ZonedDateTime>> m_lastLogin;

            final Supplier<Optional<ZonedDateTime>> m_lastUpload;

            final Supplier<Optional<ZonedDateTime>> m_lastSentLogin;

            final Supplier<Optional<ZonedDateTime>> m_lastSentUpload;

            Scope(final String parameterName, final Supplier<Optional<ZonedDateTime>> lastLogin,
                final Supplier<Optional<ZonedDateTime>> lastUpload,
                final Supplier<Optional<ZonedDateTime>> lastSentLogin,
                final Supplier<Optional<ZonedDateTime>> lastSentUpload) {
                m_parameterName = parameterName;
                m_lastLogin = lastLogin;
                m_lastUpload = lastUpload;
                m_lastSentLogin = lastSentLogin;
                m_lastSentUpload = lastSentUpload;
            }
        }

        final String m_id;

        HubUsage(final String identifier) {
            m_id = identifier;
        }

        /** Signal that hub usage for the given scope have now been reported to instrumentation */
        private static void dataSent() {
            HubStatistics.getLastLogin().ifPresent(ld -> HubStatistics//
                .storeKnimeHubStat(HubStatistics.LAST_SENT_KNIME_HUB_LOGIN, ld.toString()));
            HubStatistics.getLastUpload().ifPresent(ud -> HubStatistics//
                .storeKnimeHubStat(HubStatistics.LAST_SENT_KNIME_HUB_UPLOAD, ud.toString()));
            HubStatistics.getLastNonCommunityLogin().ifPresent(ld -> HubStatistics
                .storeKnimeHubStat(HubStatistics.LAST_SENT_KNIME_NON_COMMUNITY_HUB_LOGIN, ld.toString()));
            HubStatistics.getLastNonCommunityUpload().ifPresent(ud -> HubStatistics
                .storeKnimeHubStat(HubStatistics.LAST_SENT_KNIME_NON_COMMUNITY_HUB_UPLOAD, ud.toString()));
        }

        static HubUsage current(final Scope scope) {
            Optional<ZonedDateTime> lastLogin = Optional.empty();
            Optional<ZonedDateTime> lastUpload = Optional.empty();
            Optional<ZonedDateTime> lastSentLogin = Optional.empty();
            Optional<ZonedDateTime> lastSentUpload = Optional.empty();

            try {
                lastLogin = scope.m_lastLogin.get();
                lastUpload = scope.m_lastUpload.get();
                lastSentLogin = scope.m_lastSentLogin.get();
                lastSentUpload = scope.m_lastSentUpload.get();
            } catch (Exception e) { // NOSONAR
                NodeLogger.getLogger(WelcomeAPEndpoint.class)
                    .info("Hub statistics could not be fetched: " + e.getMessage(), e);
            }

            if (lastUpload.isPresent() && !lastUpload.equals(lastSentUpload)) {
                return CONTRIBUTOR;
            } else if (lastLogin.isPresent() && !lastLogin.equals(lastSentLogin)) {
                return USER;
            } else if (KNIMEApplication.isStartedWithFreshWorkspace()) {
                return NEW;
            }

            return NONE;
        }

        static String requestParameter(final Scope scope) {
            return scope.m_parameterName + ":" + HubUsage.current(scope).m_id;
        }
    }

}
