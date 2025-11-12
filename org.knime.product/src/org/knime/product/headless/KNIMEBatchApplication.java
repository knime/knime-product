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
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.NodeTimer;
import org.knime.product.ProductPlugin;
import org.knime.product.rcp.StatusLoggerHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

/**
 * The run method of this class is executed when KNIME is run headless, that is in batch mode.
 *
 * @author Bernd Wiswedel, University of Konstanz
 */
public class KNIMEBatchApplication implements IApplication {

    static {
        // unless the user specified this property, we set it to true here
        // (true means no icons etc will be loaded, if it is false, the
        // loading of the repository manager is likely to print many errors
        // - though it will still function)
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object start(final IApplicationContext context) throws Exception {
        // Starting the Core plugin initializes `IEarlyStartup` and runs the `EARLIEST` and `AFTER_PROFILES_SET` stage.
        CorePlugin.getInstance();

        // silence Log4j2's StatusLogger used for internal framework logging
        StatusLoggerHelper.disableStatusLogger();

        // load the ui plugin to read the preferences
        Platform.getBundle("org.knime.workbench.core").start(Bundle.START_TRANSIENT);

        String[] stringArgs = retrieveApplicationArguments(context);

        Bundle bundle = FrameworkUtil.getBundle(ProductPlugin.class);
        CheckUtils.checkState(bundle != null, "Cannot find bundle for ProductPlugin class");
        @SuppressWarnings("null")
        final BundleContext bundleContext = bundle.getBundleContext();
        final IBatchExecutor executor;
        final ServiceReference<IBatchExecutor> serviceRef = bundleContext.getServiceReference(IBatchExecutor.class);
        if (serviceRef == null) {
            return printMissingExtensionMessage();
        }
        executor = bundleContext.getService(serviceRef);
        try {
            if (executor == null) {
                return printMissingExtensionMessage();
            }
            final int exit = runBatchExecutor(executor, stringArgs);
            switch (exit) {
                // only report usage when the batch executor actually ran
                case IBatchExecutor.EXIT_ERR_EXECUTION, IBatchExecutor.EXIT_WARN, IBatchExecutor.EXIT_SUCCESS:
                    NodeTimer.GLOBAL_TIMER.performShutdown();
                    break;
                default:
                    // don't report errors during workflow load and/or batch executor usage problem (cmd line errors)
            }
            return exit;
        } finally {
            bundleContext.ungetService(serviceRef);
        }

    }

    private static int printMissingExtensionMessage() {
        System.err.println("KNIME Batch Executor is not installed. Visit "
            + "https://www.knime.com/batch-execution-in-knime to download and install the extension, then try again.");
        return IBatchExecutor.EXIT_ERR_NOT_INSTALLED;
    }

    /**
     * Returns the application's arguments as String array with the pdelaunch argument removed if applicable.
     *
     * @param context the application context
     * @return the arguments the application was called with as string array
     */
    protected String[] retrieveApplicationArguments(final IApplicationContext context) {
        Object args = context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
        String[] stringArgs;
        if (args instanceof String[]) {
            stringArgs = (String[])args;
            if (stringArgs.length > 0 && stringArgs[0].equals("-pdelaunch")) {
                String[] copy = new String[stringArgs.length - 1];
                System.arraycopy(stringArgs, 1, copy, 0, copy.length);
                stringArgs = copy;
            }
        } else if (args != null) {
            System.err.println("Unable to cast class " + args.getClass().getName()
                    + " to string array, toString() returns " + args.toString());
            stringArgs = new String[0];
        } else {
            stringArgs = new String[0];
        }
        return stringArgs;
    }

    /**
     * Execute the batch executor. Subclasses may override this method in order to invoke a special executor.
     *
     * @param args the command line arguments
     * @return the return value
     */
    protected int runBatchExecutor(final IBatchExecutor executor, final String[] args) {
        return executor.run(args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
    }
}
