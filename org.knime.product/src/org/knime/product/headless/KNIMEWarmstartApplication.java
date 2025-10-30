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

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.knime.core.internal.CorePlugin;
import org.knime.core.node.NodeLogger;
import org.knime.product.headless.WarmstartActionRegistry.WarmstartExecutionSummary;
import org.knime.product.rcp.StatusLoggerHelper;
import org.osgi.framework.Bundle;

/**
 * A minimal KNIME application for warm-starting KNIME in containers. This application initializes the core plugin,
 * runs all early startup stages, applies profiles, and executes all registered warmstart actions without executing 
 * any workflows.
 * 
 * <p>
 * The application discovers and executes all warmstart actions registered via the 
 * {@code org.knime.product.warmstartAction} extension point. This makes the system extensible and allows different
 * components to contribute their own initialization logic without tight coupling.
 * </p>
 * 
 * <p>
 * It's particularly useful for pre-warming Docker containers to reduce startup time for subsequent application runs.
 * </p>
 *
 * @author Marc Lehner, KNIME AG, Zurich, Switzerland
 * @since 5.9
 */
public class KNIMEWarmstartApplication implements IApplication {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(KNIMEWarmstartApplication.class);

    static {
        // Set headless mode - no GUI components will be loaded
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true");
        }
    }

    private static final int EXIT_CODE_OK = 0;
    private static final int EXIT_CODE_ERROR = 1;

    /**
     * {@inheritDoc}
     */
    @Override
    public Object start(final IApplicationContext context) throws Exception {
        // Force early logging to make sure we can see this in Docker logs
        System.out.println("=== KNIME WARMSTART APPLICATION STARTING ===");
        System.out.flush();

        LOGGER.info("Starting KNIME Warmstart Application");

        try {
            // Starting the Core plugin initializes `IEarlyStartup` and runs the `EARLIEST` stage
            LOGGER.debug("Initializing CorePlugin");
            System.out.println("=== WARMSTART: Initializing CorePlugin ===");
            System.out.flush();
            CorePlugin.getInstance();

            // Silence Log4j2's StatusLogger used for internal framework logging
            StatusLoggerHelper.disableStatusLogger();

            // Load the UI plugin to read the preferences - needed for proper initialization
            LOGGER.debug("Loading workbench core plugin");
            System.out.println("=== WARMSTART: Loading workbench core plugin ===");
            System.out.flush();
            try {
                Platform.getBundle("org.knime.workbench.core").start(Bundle.START_TRANSIENT);
                LOGGER.debug("Successfully loaded workbench core plugin");
            } catch (Exception e) {
                LOGGER.warn("Failed to load workbench core plugin, continuing with warmstart", e);
            }

            // Execute all registered warmstart actions
            System.out.println("=== WARMSTART: Executing warmstart actions ===");
            System.out.flush();
            LOGGER.info("Executing all registered warmstart actions");
            
            WarmstartExecutionSummary summary = WarmstartActionRegistry.executeAllActions();
            
            // Report final results
            System.out.println("=== WARMSTART: Execution Summary ===");
            System.out.println("Total actions: " + summary.totalActions());
            System.out.println("Executed: " + summary.executedActions());
            System.out.println("Successful: " + summary.successfulActions());
            System.out.println("Failed: " + summary.failedActions());
            System.out.println("Skipped: " + summary.skippedActions());
            System.out.flush();
            
            if (summary.hasFailures()) {
                LOGGER.warn("Warmstart completed with " + summary.failedActions() + " failures");
                System.out.println("=== WARMSTART: COMPLETED WITH FAILURES ===");
            } else if (summary.executedActions() > 0) {
                LOGGER.info("Warmstart completed successfully");
                System.out.println("=== WARMSTART: COMPLETED SUCCESSFULLY ===");
            } else {
                LOGGER.info("Warmstart completed (no actions to execute)");
                System.out.println("=== WARMSTART: COMPLETED (NO ACTIONS) ===");
            }
            System.out.flush();

            return summary.hasFailures() ? EXIT_CODE_ERROR : EXIT_CODE_OK;

        } catch (Exception e) {
            LOGGER.error("Warmstart application failed with exception", e);
            System.out.println("=== WARMSTART: FAILED WITH EXCEPTION ===");
            System.out.flush();
            return EXIT_CODE_ERROR;
        }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        LOGGER.debug("Stopping KNIME Warmstart Application");
        // No cleanup needed for this minimal application
    }
}