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
import org.knime.core.node.NodeLogger;

/**
 * A helper class for setting and retrieving a flag to / from the Eclipse configuration area.
 *
 * @author Marc Bux, KNIME GmbH, Berlin, Germany
 */
public final class ConfigAreaFlag {

    static Optional<Path> getPathFromLocation(final Location location) {

        final URL configURL = location.getURL();
        if (configURL == null) {
            return Optional.empty();
        }

        String path = configURL.getPath();
        if (path.matches("^/[a-zA-Z]:/.*")) {
            // Windows path with drive letter => remove first slash
            path = path.substring(1);
        }
        return Optional.of(Paths.get(path));
    }

    private final String m_configName;

    private final boolean m_defaultIfNotPresent;

    private final boolean m_defaultOnError;

    /**
     * @param configName the name by which this flag is identified
     * @param defaultIfNotPresent the default value of this flag if it is not found in the configuration area
     * @param defaultOnError the default value of this flag if an error occurs while obtaining its value from the
     *            configuration area
     */
    public ConfigAreaFlag(final String configName, final boolean defaultIfNotPresent, final boolean defaultOnError) {
        m_configName = configName;
        m_defaultIfNotPresent = defaultIfNotPresent;
        m_defaultOnError = defaultOnError;
    }

    boolean isFlagSet() {
        try {
            final Path path = getConfigPath();
            if (!Files.exists(path)) {
                return m_defaultIfNotPresent;
            }
            try (final BufferedReader reader = Files.newBufferedReader(path)) {
                return Boolean.parseBoolean(reader.readLine());
            }
        } catch (final IOException e) {
            NodeLogger.getLogger(ConfigAreaFlag.class)
                .error(String.format("Error when reading %s settings from configuration area.", m_configName), e);
            return m_defaultOnError;
        }
    }

    void setFlag(final boolean checkForDefenderOnStartup) {
        try {
            final Path path = getConfigPath();
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
            }
            final byte[] bytes = Boolean.toString(checkForDefenderOnStartup).getBytes();
            Files.write(path, bytes);
        } catch (final IOException e) {
            NodeLogger.getLogger(ConfigAreaFlag.class)
                .error(String.format("Error when writing %s settings to configuration area.", m_configName), e);
        }
    }

    private Path getConfigPath() throws IOException {
        final Location configLocation = Platform.getConfigurationLocation();
        if (configLocation == null) {
            throw new IOException("No configuration area set.");
        }

        final Optional<Path> configPath = getPathFromLocation(configLocation);
        if (configPath.isPresent()) {
            return configPath.get().resolve(getClass().getPackage().getName()).resolve(m_configName);
        } else {
            throw new IOException("Configuration path cannot be resolved.");
        }
    }
}
