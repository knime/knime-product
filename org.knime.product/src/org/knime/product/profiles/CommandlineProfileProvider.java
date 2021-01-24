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
 *   02.02.2018 (thor): created
 */
package org.knime.product.profiles;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.runtime.Platform;
import org.knime.product.ProductPlugin;

/**
 * Implementation of a profile provider that reads the required parameters from the command line. It reads the
 * application argument <tt>-profileList</tt> and <tt>-profileLocation</tt> and returns those values. The list should be
 * a comma- or colon-separated list of strings whereas the location should a URI (or an absolute file system path).
 *
 * @author Thorsten Meinl, KNIME AG, Zurich, Switzerland
 */
class CommandlineProfileProvider implements IProfileProvider {
    private List<String> m_requestedProfiles = Collections.emptyList();

    private URI m_profilesLocation;

    /**
     * Creates a new profile provider.
     */
    CommandlineProfileProvider() {
        String[] args = Platform.getApplicationArgs();
        for (int i = 0; i < args.length; i++) {
            if ("-profileList".equals(args[i])) {
                if (i + 1 < args.length) {
                    m_requestedProfiles = Arrays.asList(args[++i].split("[,;:]"));
                }
                // else ignore because there is no value
            } else if ("-profileLocation".equals(args[i])) {
                if (i + 1 < args.length) {
                    try {
                        m_profilesLocation = new URI(args[++i]);
                        if (m_profilesLocation.getScheme() == null) {
                            Path argPath = Paths.get(args[i]); // NOSONAR The path being user-controlled is exactly
                            // the intended usage.
                            Optional<Path> installPath = ProductPlugin.getInstallationLocation();
                            // AP-15232 -- resolve relative to installation location. Corner cases:
                            //  - If "argPath" is absolute, this trivially resolves to only "argPath".
                            //  - if install location can't be determined (empty optional) an error is
                            //    logged in ProductPlugin and "argPath" is used as-is
                            if (installPath.isPresent()) {
                                argPath = installPath.get().resolve(argPath);
                            }
                            m_profilesLocation = argPath.toAbsolutePath().toUri();
                        }
                    } catch (URISyntaxException ex) {
                        Path p = Paths.get(args[i]);
                        m_profilesLocation = p.toUri();
                    }
                }
                // else ignore because there is no value
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getRequestedProfiles() {
        return m_requestedProfiles;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URI getProfilesLocation() {
        if (m_profilesLocation == null) {
            throw new IllegalArgumentException("No profile location was provided");
        }
        return m_profilesLocation;
    }

}
