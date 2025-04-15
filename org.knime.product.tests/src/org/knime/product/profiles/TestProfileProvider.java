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
 *   11.02.2018 (thor): created
 */
package org.knime.product.profiles;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.runtime.FileLocator;
import org.osgi.framework.FrameworkUtil;

/**
 * Profile provider for testing the {@link ProfileManager}.
 * <p>
 * This file additionally provides two different instances of this {@link TestProfileProvider}, whose return values
 * (profile list and location) can be edited via static instance getters.
 * </p>
 *
 * @author Thorsten Meinl, KNIME AG, Zurich, Switzerland
 * @author Leon Wenzler, KNIME GmbH, Konstanz, Germany
 */
public abstract class TestProfileProvider implements IProfileProvider {

    static final String TEST_PROFILES_LOCATION = "test-profiles";

    private List<String> m_profiles = Arrays.asList("base", "custom");

    private Optional<URI> m_location = Optional.empty();

    /**
     * Sets the list of profiles returned by {@link #getRequestedProfiles()}.
     *
     * @param profiles list of profiles
     */
    public void setRequestedProfiles(final List<String> profiles) {
        m_profiles = profiles;
    }

    @Override
    public List<String> getRequestedProfiles() {
        return m_profiles;
    }

    /**
     * Sets the profiles location returned by {@link #getProfilesLocation()}.
     *
     * @param location URI of the profiles location
     */
    public void setProfilesLocation(final URI location) {
        m_location = Optional.ofNullable(location);
    }

    @Override
    public URI getProfilesLocation() {
        if (m_location.isPresent()) {
            return m_location.get();
        }
        Enumeration<URL> profileUrls =
            FrameworkUtil.getBundle(getClass()).findEntries("/", TEST_PROFILES_LOCATION, false);
        try {
            String url = FileLocator.toFileURL(profileUrls.nextElement()).toString();
            return new URI(url.replace(" ", "%20")).normalize();
        } catch (IOException | URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Optional<String> resolveVariable(final String name) {
        return Optional.ofNullable("var".equals(name) ? "replaced-value" : null);
    }

    /**
     * Low-priority profile provider, loaded before {@link HighPriority}.
     */
    public static class LowPriority extends TestProfileProvider {

        private static TestProfileProvider instance = null;

        /**
         * Creates an instance of a {@link TestProfileProvider}.
         */
        public LowPriority() {
            instance = this;
        }

        /**
         * Returns the last-created instance of this class.
         *
         * @return (high-priority) profile provider for testing
         */
        public static TestProfileProvider getInstance() {
            return instance;
        }
    }

    /**
     * High-priority profile provider, overwrites preferences from {@link LowPriority}.
     */
    public static class HighPriority extends TestProfileProvider {

        private static TestProfileProvider instance = null;

        /**
         * Creates an instance of a {@link TestProfileProvider}.
         */
        public HighPriority() {
            instance = this;
        }

        /**
         * Returns the last-created instance of this class.
         *
         * @return (high-priority) profile provider for testing
         */
        public static TestProfileProvider getInstance() {
            return instance;
        }
    }
}
