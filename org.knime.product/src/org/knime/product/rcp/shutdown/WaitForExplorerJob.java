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
 *   29 Oct 2024 (Manuel Hotz, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.product.rcp.shutdown;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;
import org.eclipse.ui.internal.progress.ProgressMonitorJobsDialog;
import org.knime.product.ProductPlugin;
import org.knime.workbench.explorer.view.ExplorerJob;

/**
 * Pre-shutdown hook that delays shutdown, prompting the user to wait for (remote) Explorer jobs, cancel jobs, or cancel
 * the non-forced shutdown process.
 *
 * @author Manuel Hotz, KNIME GmbH, Konstanz, Germany
 * @author Leon Wenzler, KNIME GmbH, Konstanz, Germany
 */
public final class WaitForExplorerJob implements PreShutdown {

    // this class cannot live in org.knime.workbench.explorer.view, because that does not have a dependency on product,
    // where this extension point is defined

    @SuppressWarnings("restriction")
    @Override
    public boolean onPreShutdown() {
        // no need to show dialog if no explorer job is running
        if (!hasRunningExplorerJobs() || GraphicsEnvironment.isHeadless()) {
            return true;
        }

        // continue to wait with dialog for finishing, or send signal to terminate early
        final var display = Display.getCurrent();
        final var dialog = new WaitForExplorerJobsDialog(display != null ? display.getActiveShell() : null);
        final var status = new MultiStatus(ProductPlugin.PLUGIN_ID, 1, "Error while waiting for operations to finish");
        try {
            dialog.run(true, true, monitor -> {
                Job.getJobManager().join(ExplorerJob.SERVER_JOB_FAMILY, monitor);
                status.merge(new Status(IStatus.OK, ProductPlugin.PLUGIN_ID, "Operations finished"));
            });
        } catch (final InvocationTargetException e) { // NOSONAR status is logged
            status.merge(new Status(IStatus.ERROR, ProductPlugin.PLUGIN_ID, 1, IDEWorkbenchMessages.InternalError,
                e.getTargetException()));
        } catch (final InterruptedException e) { // NOSONAR status is logged
            // waiting for jobs to finish was interrupted by "Cancel" or "Cancel all operations"
            if (dialog.waitForExplorerJobs()) {
                status.merge(new Status(IStatus.OK, ProductPlugin.PLUGIN_ID, "Waiting for operations to finish"));
            } else {
                status.merge(new Status(IStatus.OK, ProductPlugin.PLUGIN_ID, "Terminated all operations"));
            }
            Thread.currentThread().interrupt();
        } finally {
            dialog.close();
        }

        if (!status.isOK()) {
            ErrorDialog.openError(null, "Error while waiting for operations to finish", null, status,
                IStatus.ERROR | IStatus.WARNING);
            Platform.getLog(Platform.getBundle(ProductPlugin.PLUGIN_ID)).log(status);
        }

        // have to check for running jobs again, user could have waiting with dialog, i.e. does
        // not want to terminate, but wants to continue exiting the AP
        return !dialog.waitForExplorerJobs() || !hasRunningExplorerJobs();
    }

    @Override
    public void onShutdownContinued() {
        // nothing to do, the continued shutdown already terminates all running jobs

        // additionally, we accept non-reversible job termination on abort of the shutdown because it is
        // not easily possible and the user agreed to terminating the all running explorer jobs in this case
    }

    static boolean hasRunningExplorerJobs() {
        final var jobs = Job.getJobManager().find(ExplorerJob.SERVER_JOB_FAMILY);
        return jobs != null && jobs.length > 0;
    }

    /**
     * Progress-showing dialog of currently running Eclipse background {@link Job}s, similar to what
     * you would see when closing the Eclipse IDE before all its background jobs finished.
     *
     * @author Manuel Hotz, KNIME GmbH, Konstanz, Germany
     */
    @SuppressWarnings("restriction")
    private static class WaitForExplorerJobsDialog extends ProgressMonitorJobsDialog {

        // note that what we call 'operations' here, are technically (progress-reporting) 'jobs'

        final AtomicBoolean m_terminateAllJobs = new AtomicBoolean();

        public WaitForExplorerJobsDialog(final Shell parent) {
            super(parent);
        }

        /**
         * Whether the dialog input was to was for jobs. If {@code false}, this is equivalent to
         * terminating all jobs immediately.
         *
         * @return whether to wait for explorer jobs
         */
        public boolean waitForExplorerJobs() {
            return !m_terminateAllJobs.get();
        }

        @Override
        protected void createButtonsForButtonBar(final Composite parent) {
            super.createButtonsForButtonBar(parent);
            final var terminateAllButton = createButton(parent, OK, "Terminate all operations", false);
            terminateAllButton.setToolTipText("Terminate all shown operations and proceed with shutdown");
            terminateAllButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(final SelectionEvent e) {
                    Job.getJobManager().cancel(ExplorerJob.SERVER_JOB_FAMILY);
                    getProgressMonitor().setCanceled(true);
                    m_terminateAllJobs.set(true);
                }
            });
        }
    }
}
