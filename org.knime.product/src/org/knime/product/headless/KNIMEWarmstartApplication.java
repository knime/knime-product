/*
 * ------------------------------------------------------------------------
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
 * ----------------------------------------------------------------------------
 */
package org.knime.product.headless;

/**
 * A minimal KNIME application for warm-starting KNIME in containers. This application initializes the core plugin,
 * runs all early startup stages, applies profiles, and performs other initialization tasks without executing any
 * workflows. It's particularly useful for pre-warming Docker containers to reduce startup time for subsequent
 * application runs.
 *
 * @author Marc Lehner, KNIME AG, Zurich, Switzerland
 * @since 5.9
 */
public class KNIMEWarmstartApplication {

    static {
        // Ensure headless mode - this is also set in VM arguments but double-check here
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true");
        }
    }

    /**
     * Run method that Eclipse application framework expects.
     */
    public Object run(final Object context) throws Exception {
        return start(context);
    }

    /**
     * Start method for the warmstart application.
     */
    public Object start(final Object context) throws Exception {
        System.out.println("Starting KNIME Warmstart Application");

        try {
            // Use reflection to avoid classpath issues during class loading
            
            // Starting the Core plugin initializes `IEarlyStartup` and runs the `EARLIEST` stage
            System.out.println("Initializing CorePlugin");
            Class<?> corePluginClass = Class.forName("org.knime.core.internal.CorePlugin");
            Object corePlugin = corePluginClass.getMethod("getInstance").invoke(null);

            // Silence Log4j2's StatusLogger used for internal framework logging
            System.out.println("Disabling StatusLogger");
            Class<?> statusLoggerHelperClass = Class.forName("org.knime.product.rcp.StatusLoggerHelper");
            statusLoggerHelperClass.getMethod("disableStatusLogger").invoke(null);

            // Load the UI plugin to read the preferences - needed for proper initialization
            System.out.println("Starting workbench.core bundle");
            Class<?> platformClass = Class.forName("org.eclipse.core.runtime.Platform");
            Object bundle = platformClass.getMethod("getBundle", String.class).invoke(null, "org.knime.workbench.core");
            Class<?> bundleClass = Class.forName("org.osgi.framework.Bundle");
            Object startTransient = bundleClass.getField("START_TRANSIENT").get(null);
            bundleClass.getMethod("start", int.class).invoke(bundle, startTransient);

            // Apply profiles - this triggers profile-dependent initialization
            // Note: CorePlugin.getInstance() already runs both EARLIEST and AFTER_PROFILES_SET stages,
            // so we don't need to manually call runAfterProfilesLoaded()
            System.out.println("Applying profiles");
            Class<?> profileManagerClass = Class.forName("org.knime.product.profiles.ProfileManager");
            Object profileManager = profileManagerClass.getMethod("getInstance").invoke(null);
            profileManagerClass.getMethod("applyProfiles").invoke(profileManager);

            System.out.println("All early startup stages completed automatically by CorePlugin initialization");

            System.out.println("KNIME Warmstart Application completed successfully");
            
            // Return IApplication.EXIT_OK (which is 0)
            return Integer.valueOf(0);

        } catch (Exception e) {
            System.err.println("Error during warmstart application execution: " + e.getMessage());
            e.printStackTrace();
            return Integer.valueOf(1); // Return error code
        }
    }

    /**
     * Stop method for the warmstart application.
     */
    public void stop() {
        System.out.println("Stopping KNIME Warmstart Application");
        // No cleanup needed for this minimal application
    }
}