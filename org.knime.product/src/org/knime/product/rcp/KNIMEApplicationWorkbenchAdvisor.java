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
package org.knime.product.rcp;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.equinox.internal.p2.ui.sdk.scheduler.AutomaticUpdatePlugin;
import org.eclipse.jface.preference.IPreferenceNode;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.IWorkbenchConfigurer;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;
import org.knime.core.eclipseUtil.EclipseProxyServiceInitializer;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.ui.util.SWTUtilities;
import org.knime.core.util.EclipseUtil;
import org.knime.product.rcp.shutdown.PreShutdown;
import org.knime.product.rcp.startup.LongStartupHandler;
import org.knime.workbench.core.KNIMECorePlugin;
import org.knime.workbench.core.preferences.HeadlessPreferencesConstants;
import org.knime.workbench.core.util.LinkMessageDialog;
import org.osgi.service.prefs.Preferences;

/**
 * Provides the initial workbench perspective ID (KNIME perspective).
 *
 * @author Florian Georg, University of Konstanz
 * @author Thorsten Meinl, KNIME AG, Zurich, Switzerland
 */
@SuppressWarnings("restriction") // internal SDK scheduler class usage.
public class KNIMEApplicationWorkbenchAdvisor extends WorkbenchAdvisor {

    private final KNIMEOpenDocumentEventProcessor m_openDocProcessor;

    private final KNIMEOpenUrlEventProcessor m_openUrlProcessor;

    /**
     * Simple constructor to store the {@code KNIMEOpenDocumentEventProcessor}
     *
     * @param openDocProcessor the {@link KNIMEOpenDocumentEventProcessor} handling the opening of KNIME files
     * @param openUrlProcessor the {@link KNIMEOpenUrlEventProcessor} handling the opening of knime:// URLs
     *
     */
    public KNIMEApplicationWorkbenchAdvisor(final KNIMEOpenDocumentEventProcessor openDocProcessor,
        final KNIMEOpenUrlEventProcessor openUrlProcessor) {
        m_openDocProcessor = openDocProcessor;
        m_openUrlProcessor = openUrlProcessor;
    }

    @Override
    public String getInitialWindowPerspectiveId() {
        return "org.knime.ui.java.perspective";
    }

    /**
     * Initializes the application. At the moment it just forces the product to save and restore the window and
     * perspective settings (remembers whether editors are open, etc.).
     *
     * @param configurer an object for configuring the workbench
     *
     *
     * @see org.eclipse.ui.application.WorkbenchAdvisor #initialize(org.eclipse.ui.application.IWorkbenchConfigurer)
     */
    @Override
    public void initialize(final IWorkbenchConfigurer configurer) {
        super.initialize(configurer);

        configurer.setSaveAndRestore(true);
    }

    @Override
    public WorkbenchWindowAdvisor createWorkbenchWindowAdvisor(final IWorkbenchWindowConfigurer configurer) {
        return new KNIMEApplicationWorkbenchWindowAdvisor(configurer);
    }

    @Override
    public void preStartup() {
        super.preStartup();

        if (!EclipseUtil.isRunFromSDK()) {
            if (KNIMEApplication.isStartedWithFreshWorkspace()) {
                KNIMEConstants.GLOBAL_THREAD_POOL.enqueue(new ExampleWorkflowExtractor());
            }
            changeDefaultPreferences();
        }
    }

    @Override
    public boolean preShutdown() {
        super.preShutdown();
        return PreShutdown.preShutdown();
    }

    @Override
    public void eventLoopIdle(final Display display) {
        m_openDocProcessor.openFiles();
        m_openUrlProcessor.openUrls();
        super.eventLoopIdle(display);
    }

    @Override
    public void postStartup() {
        super.postStartup();

        // Initialize the proxy service from org.eclipse.core.net so that any updates sites
        // behind an authenticated proxy can be reached (the service supplies configuration)
        EclipseProxyServiceInitializer.ensureInitialized();

        // Remove preference pages we don't want to expose to our users
        final var preferenceRoot = PlatformUI.getWorkbench().getPreferenceManager();

        // root preference pages
        preferenceRoot.remove("org.eclipse.ant.ui.AntPreferencePage");
        preferenceRoot.remove("org.eclipse.datatools.connectivity.ui.preferences.dataNode");
        preferenceRoot.remove("org.eclipse.debug.ui.DebugPreferencePage");
        preferenceRoot.remove("org.eclipse.emf.validation.ui.rootPage");
        preferenceRoot.remove("org.eclipse.help.ui.browsersPreferencePage");
        preferenceRoot.remove("org.eclipse.jdt.ui.preferences.JavaBasePreferencePage");
        preferenceRoot.remove("org.eclipse.pde.ui.MainPreferencePage");
        preferenceRoot.remove("org.eclipse.team.ui.TeamPreferences");
        preferenceRoot.remove("org.eclipse.wst.xml.ui.preferences.xml");
        preferenceRoot.remove("ValidationPreferencePage");

        // Pages below the 'General' preference page
        IPreferenceNode generalPage = preferenceRoot.find("org.eclipse.ui.preferencePages.Workbench");
        generalPage.remove("org.eclipse.compare.internal.ComparePreferencePage");
        generalPage.remove("org.eclipse.search.preferences.SearchPreferencePage");
        generalPage.remove("org.eclipse.text.quicksearch.PreferencesPage");
        generalPage.remove("org.eclipse.ui.monitoring.page");
        generalPage.remove("org.eclipse.ui.preferencePages.ContentTypes");
        generalPage.remove("org.eclipse.ui.preferencePages.Editors");
        generalPage.remove("org.eclipse.ui.preferencePages.General.LinkHandlers");
        generalPage.remove("org.eclipse.ui.preferencePages.Globalization");
        generalPage.remove("org.eclipse.ui.preferencePages.Perspectives");
        generalPage.remove("org.eclipse.ui.trace.tracingPage");

        // Pages below the 'Install/Update' preference page
        IPreferenceNode installUpdatePage =
            preferenceRoot.find("org.eclipse.equinox.internal.p2.ui.sdk.ProvisioningPreferencePage");
        installUpdatePage.remove("org.eclipse.equinox.internal.p2.ui.sdk.scheduler.AutomaticUpdatesPreferencePage");

        // Pages below General -> Appearance
        IPreferenceNode views = generalPage.findSubNode("org.eclipse.ui.preferencePages.Views");
        views.remove("org.eclipse.ui.preferencePages.Decorators");

        SWTUtilities.markKNIMEShell();

        LongStartupHandler.getInstance().onStartupConcluded();

        checkAnonymousUsageStatistics(SWTUtilities.getKNIMEWorkbenchShell());
        if (KNIMEApplication.isUITestingMode()) {
            // when running in Xvfb, maximizing is not enough, we need to
            // manually resize the application window to fit the display
            var shell = SWTUtilities.getKNIMEWorkbenchShell();
            var area = shell.getDisplay().getPrimaryMonitor().getClientArea();
            shell.setSize(area.width, area.height);
            shell.setMaximized(true);
            System.out.println( "[UI Testing Mode]: maximizing window to: width = " + area.width + "; height = " + area.height); // NOSONAR
        }
    }

    /**
     * Asks the user to send anonymous usage statistics to KNIME on a new workspace instance.
     */
    private static void checkAnonymousUsageStatistics(final Shell shell) {
        IPreferenceStore pStore = KNIMECorePlugin.getDefault().getPreferenceStore();
        final var alreadyAsked = pStore.getBoolean(HeadlessPreferencesConstants.P_ASKED_ABOUT_STATISTICS);
        //pStore.setDefault(HeadlessPreferencesConstants.P_SEND_ANONYMOUS_STATISTICS, false);
        if (alreadyAsked || KNIMEApplication.isUITestingMode()) {
            return;
        }
        final var message = "Help us to further improve KNIME Analytics Platform by sending us anonymous usage data. "
            + "The data collected is used for recommendations of the new built-in Workflow Coach. "
            + "Click <a href=\"https://www.knime.com/faq#usage_data\">here</a> to find out what is being transmitted. "
            + "You can also change this setting in the KNIME preferences later.\n\n"
            + "Do you allow KNIME to collect and send anonymous usage data? "
            + "This will also enable the Workflow Coach.";
        final var allow = LinkMessageDialog.openQuestion(shell, "Help improve KNIME", message);
        pStore.setValue(HeadlessPreferencesConstants.P_ASKED_ABOUT_STATISTICS, true);
        pStore.setValue(HeadlessPreferencesConstants.P_SEND_ANONYMOUS_STATISTICS, allow);
    }

    @Override
    public void postShutdown() {
        super.postShutdown();

        final var workspace = ResourcesPlugin.getWorkspace();
        if (workspace != null) {
            try {
                workspace.save(true, null);
            } catch (CoreException ex) {
                Platform.getLog(Platform.getBundle("org.knime.product")) //
                    .log(new Status(IStatus.ERROR, "org.knime.product", "Error while saving workspace", ex));
            }
        }
    }

    private static void changeDefaultPreferences() {
        // disable automatic update check, we have our own 'org.knime.core.eclipseUtil.UpdateChecker'
        final Preferences node = DefaultScope.INSTANCE.getNode(AutomaticUpdatePlugin.PLUGIN_ID);
        node.putBoolean(org.eclipse.equinox.internal.p2.ui.sdk.scheduler.PreferenceConstants.PREF_AUTO_UPDATE_ENABLED,
            false);
    }
}
