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
 *   Apr 3, 2025 (lw): created
 */
package org.knime.product.profiles;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.function.Supplier;

import org.apache.http.client.utils.URIBuilder;
import org.eclipse.core.runtime.FileLocator;
import org.osgi.framework.FrameworkUtil;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import jakarta.ws.rs.core.HttpHeaders;

final class TestProfileServer {

    private static final String WIREMOCK_FILES_LOCATION = "__files";

    private final WireMockServer m_server;

    private final String m_path;

    /**
     * Creates a WireMock server that will serve the given ZIP file at the specified path.
     *
     * @param filePath the local filesystem path to the ZIP file (e.g., "profiles.zip")
     * @param servePath the HTTP path at which the file should be served (e.g., "/profiles")
     */
    public TestProfileServer(final String filePath, final String servePath) {
        m_path = servePath;

        // Retrieving path where the our file is stored (must be within "test-profiles").
        // Then, configure this path as root and only use "filePath" as name.
        Path path;
        final var urls = FrameworkUtil.getBundle(getClass()).findEntries("/" + //
            TestProfileProvider.TEST_PROFILES_LOCATION, //
            WIREMOCK_FILES_LOCATION, false);
        try {
            final var url = FileLocator.toFileURL(urls.nextElement());
            path = Path.of(url.toURI()).normalize().getParent();
        } catch (IOException | URISyntaxException ex) {
            throw new RuntimeException(ex);
        }

        // Define server with custom root and a ZIP-file-serving response.
        m_server = loadInWireMockContext(() -> {
            final var server = new WireMockServer(wireMockConfig() //
                .dynamicPort() //
                .usingFilesUnderDirectory(path.toAbsolutePath().toString()));
            server.stubFor(get(urlPathEqualTo(m_path)) //
                .willReturn(aResponse() //
                    .withHeader(HttpHeaders.CONTENT_TYPE, ProfileManager.PREFERENCES_MEDIA_TYPE) //
                    .withBodyFile(filePath)));
            return server;
        });
    }

    /**
     * We need to load the {@link WireMockConfiguration} with its own classloader, otherwise it won't find
     * a certain file upon initializing the object.
     *
     * @return WireMockConfiguration
     */
    private static <T> T loadInWireMockContext(final Supplier<T> supplier) {
        // Setting the context classloader is necessary, otherwise Wiremock doesn't find its default 'keystore' file.
        final var previousCl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(WireMock.class.getClassLoader());
        try {
            return supplier.get();
        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
    }

    /**
     * Starts the {@link WireMock} server.
     */
    public void start() {
        m_server.start();
    }

    /**
     * Stops the {@link WireMock} server.
     */
    public void stop() {
        m_server.stop();
    }

    /**
     * Returns the {@link URI} where the {@code profiles.zip} is served.
     *
     * @return URI with path, serving file
     */
    public URI getProfilesLocation() {
        return loadInWireMockContext(() -> {
            try {
                return new URIBuilder(m_server.baseUrl()).setPath(m_path).build();
            } catch (URISyntaxException ex) { // NOSONAR, should not happen
                return null;
            }
        });
    }
}
