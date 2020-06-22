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
 *   19 Jun 2020 (Marc Bux, KNIME GmbH, Berlin, Germany): created
 */
package org.knime.product.rcp.startup;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.eclipse.core.runtime.Platform;
import org.eclipse.osgi.service.datalocation.Location;

/**
 * A helper class for setting and retrieving a flag to / from the Eclipse configuration area. We use the Eclipse
 * configuration area (as opposed to the KNIME configuration area), since we want to delay calling code from other KNIME
 * plugins.
 *
 * @author Marc Bux, KNIME GmbH, Berlin, Germany
 */
final class ConfigAreaFlag {

    static Optional<Path> getPathFromLocation(final Location location) {
        // code copied from ConfigurationAreaChecker#getConfigurationLocationPath
        final URL configURL = location.getURL();
        if (configURL != null) {
            String path = configURL.getPath();
            if (Platform.OS_WIN32.equals(Platform.getOS()) && path.matches("^/[a-zA-Z]:/.*")) {
                // Windows path with drive letter => remove first slash
                path = path.substring(1);
            }
            return Optional.of(Paths.get(path));
        }
        return Optional.empty();
    }

    private final String m_key;

    private final DelayedMessageLogger m_logger;

    ConfigAreaFlag(final String configName, final DelayedMessageLogger logger) {
        m_key = configName;
        m_logger = logger;
    }

    boolean isFlagSet() {
        try {
            final Path path = getConfigPath();
            if (Files.exists(path)) {
                try (final BufferedReader reader = Files.newBufferedReader(path)) {
                    return Boolean.parseBoolean(reader.readLine());
                }
            }
        } catch (final IOException e) {
            m_logger.queueError(String.format("Error when reading %s settings from configuration area.", m_key), e);
        }
        return false;
    }

    void setFlag(final boolean value) {
        try {
            final Path path = getConfigPath();
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
            }
            final byte[] bytes = Boolean.toString(value).getBytes();
            Files.write(path, bytes);
        } catch (final IOException e) {
            m_logger.queueError(String.format("Error when writing %s settings to configuration area.", m_key), e);
        }
    }

    private Path getConfigPath() throws IOException {
        // code mostly copied from org.knime.core.internal.ConfigurationAreaChecker#getConfigurationLocationPath
        final Location configLocation = Platform.getConfigurationLocation();
        if (configLocation == null) {
            throw new IOException("No configuration area set.");
        }

        final Optional<Path> configPath = getPathFromLocation(configLocation);
        if (configPath.isPresent()) {
            // use same folder as org.knime.core.node.KNIMEConstants#assignUniqueID
            return configPath.get().resolve("org.knime.core").resolve(m_key);
        } else {
            throw new IOException("Configuration path cannot be resolved.");
        }
    }
}
