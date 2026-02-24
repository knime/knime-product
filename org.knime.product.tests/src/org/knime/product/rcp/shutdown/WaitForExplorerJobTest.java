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
 *   Nov 4, 2024 (lw): created
 */
package org.knime.product.rcp.shutdown;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.GraphicsEnvironment;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.junit.jupiter.api.Test;
import org.knime.workbench.explorer.view.ExplorerJob;

/**
 * Tests whether the {@link WaitForExplorerJob} detects and waits for running background {@link Job}s.
 */
class WaitForExplorerJobTest {

    private static final String PROPERTY_HEADLESS = "java.awt.headless";

    /**
     * Unfortunately, {@link GraphicsEnvironment#isHeadless()} caches its value after being
     * queried once. Below, we want to set {@code java.awt.headless=true} in a section,
     * irrespective of what other tests do (incl. querying {@link GraphicsEnvironment}).
     * <p>
     * Using reflection, this method resets the cached value in {@link GraphicsEnvironment},
     * sets @{code java.awt.headless=newValue} and returns the previous {@code oldValue}.
     * </p>
     *
     * @param newValue new value to be set as headless property.
     * @return oldValue {@link String} value to what {@value #PROPERTY_HEADLESS} was set before.
     */
    private static final String setHeadlessPropertyReflective(final boolean newValue) {
        final var oldValue = System.setProperty(PROPERTY_HEADLESS, Boolean.toString(newValue));
        resetHeadlessCacheReflective();
        assertEquals(newValue, GraphicsEnvironment.isHeadless(),
            "GraphicsEnvironment should have picked up new headless value");
        return oldValue;
    }

    private static void resetHeadlessCacheReflective() {
        try {
            final var field = GraphicsEnvironment.class.getDeclaredField("headless");
            field.setAccessible(true);
            field.set(/*static*/null, null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<IConfigurationElement, PreShutdown> createWaitForExplorerJobMap() {
        final var waitForExplorerHook = new WaitForExplorerJob();
        final var configElement = new PreShutdownTest.TestingConfigurationElement() {

            @Override
            public Object createExecutableExtension(final String propertyName) throws CoreException {
                return waitForExplorerHook;
            }
        };
        return Map.of(configElement, waitForExplorerHook);
    }

    @Test
    void testNoRunningJobs() {
        assertThat("Should not have detected any running jobs", WaitForExplorerJob.hasRunningExplorerJobs());
    }

    @Test
    void testDetectRunningJobs() {
        final var job = new WaitingJob();
        job.schedule();
        assertThat("Should have detected running jobs", WaitForExplorerJob.hasRunningExplorerJobs());

        // ensure that we are not trying to open the dialog in tests (AP-24775)
        final var value = setHeadlessPropertyReflective(true);
        try {
            final var shutdown = PreShutdown.preShutdown(createWaitForExplorerJobMap());
            assertThat("Jobs should be terminated since we are in headless environment", shutdown);
        } finally {
            if (value != null) {
                System.setProperty(PROPERTY_HEADLESS, value);
            } else {
                System.clearProperty(PROPERTY_HEADLESS);
            }
            // ensure that next queries to `GraphicsEnvironment#isHeadless` use System property
            resetHeadlessCacheReflective();
            job.terminate();
        }
    }

    private static class WaitingJob extends ExplorerJob {

        private boolean m_isWaiting;

        public WaitingJob() {
            super(WaitingJob.class.getName());
        }

        public synchronized void terminate() {
            m_isWaiting = true;
            notify();
            this.cancel();
        }

        @Override
        protected synchronized IStatus run(final IProgressMonitor monitor) {
            m_isWaiting = true;
            while (m_isWaiting) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    monitor.setCanceled(true);
                    return new Status(IStatus.ERROR, getClass(), "");
                }
            }
            return new Status(IStatus.OK, getClass(), "");
        }
    }
}
