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
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.internal.p2.ui.sdk.scheduler.AutomaticUpdateMessages;
import org.eclipse.equinox.internal.p2.ui.sdk.scheduler.AutomaticUpdatePlugin;
import org.eclipse.equinox.internal.p2.ui.sdk.scheduler.AutomaticUpdateScheduler;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceNode;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.IWorkbenchConfigurer;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;
import org.eclipse.ui.internal.Workbench;
import org.knime.core.eclipseUtil.EclipseProxyServiceInitializer;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.ui.util.SWTUtilities;
import org.knime.core.util.EclipseUtil;
import org.knime.product.rcp.intro.IntroPage;
import org.knime.product.rcp.shutdown.PreShutdown;
import org.knime.product.rcp.startup.LongStartupHandler;
import org.osgi.framework.Bundle;
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
    private final IniChangedChecker m_iniChangedChecker;

    /**
     * Simple constructor to store the {@code KNIMEOpenDocumentEventProcessor}
     *
     * @param openDocProcessor the {@link KNIMEOpenDocumentEventProcessor} handling the opening of KNIME files
     * @param openUrlProcessor the {@link KNIMEOpenUrlEventProcessor} handling the opening of knime:// URLs
     * @param iniChangedChecker the {@link IniChangedChecker} handling the checking of changes to knime.ini
     *
     */
    public KNIMEApplicationWorkbenchAdvisor(final KNIMEOpenDocumentEventProcessor openDocProcessor,
        final KNIMEOpenUrlEventProcessor openUrlProcessor, final IniChangedChecker iniChangedChecker) {
        m_openDocProcessor = openDocProcessor;
        m_openUrlProcessor = openUrlProcessor;
        m_iniChangedChecker = iniChangedChecker;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public WorkbenchWindowAdvisor createWorkbenchWindowAdvisor(final IWorkbenchWindowConfigurer configurer) {
        return new KNIMEApplicationWorkbenchWindowAdvisor(configurer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preStartup() {
        super.preStartup();

        if (!EclipseUtil.isRunFromSDK()) {
            if (IntroPage.INSTANCE.isFreshWorkspace()) {
                KNIMEConstants.GLOBAL_THREAD_POOL.enqueue(new ExampleWorkflowExtractor());
            }
            changeDefaultPreferences();
        }
    }

    @Override
    public boolean preShutdown() {
        super.preShutdown();
        // if run from SDK, then Workbench does not set 'PROP_EXIT_CODE' since there is no command_line
        m_iniChangedChecker.digestIni();

        // only show dialog when restart/reload and not shutdown requested...
        final var ec = System.getProperty(Workbench.PROP_EXIT_CODE);
        if (m_iniChangedChecker.iniDidChange()
                && (IApplication.EXIT_RELAUNCH.toString().equals(ec)
                        || IApplication.EXIT_RESTART.toString().equals(ec))) {
            MessageDialog.openInformation(null, "Manual restart required",
                 "KNIME Analytics Platform will be shut down completely to load updates to the program configuration. "
                 + "Manually reopen the application to complete the restart.");
        }

        return PreShutdown.preShutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventLoopIdle(final Display display) {
        m_openDocProcessor.openFiles();
        m_openUrlProcessor.openUrls();
        super.eventLoopIdle(display);
    }

    /**
     * {@inheritDoc}
     */
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

        // Pages below General -> Appearance
        IPreferenceNode views = generalPage.findSubNode("org.eclipse.ui.preferencePages.Views");
        views.remove("org.eclipse.ui.preferencePages.Decorators");

        SWTUtilities.markKNIMEShell();

        LongStartupHandler.getInstance().onStartupConcluded();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postShutdown() {
        super.postShutdown();

        if (ResourcesPlugin.getWorkspace() != null) {
            try {
                ResourcesPlugin.getWorkspace().save(true, null);
            } catch (CoreException ex) {
                Bundle myself = Platform.getBundle("org.knime.product");
                Status error = new Status(IStatus.ERROR, "org.knime.product", "Error while saving workspace", ex);
                Platform.getLog(myself).log(error);
            }
        }
    }


    private static void changeDefaultPreferences() {
        // enable automatic check for updates every day at 11:00
        final Preferences node = DefaultScope.INSTANCE.getNode(AutomaticUpdatePlugin.PLUGIN_ID);
        node.putBoolean(org.eclipse.equinox.internal.p2.ui.sdk.scheduler.PreferenceConstants.PREF_AUTO_UPDATE_ENABLED,
            true);
        node.put(org.eclipse.equinox.internal.p2.ui.sdk.scheduler.PreferenceConstants.PREF_AUTO_UPDATE_SCHEDULE,
            org.eclipse.equinox.internal.p2.ui.sdk.scheduler.PreferenceConstants.PREF_UPDATE_ON_FUZZY_SCHEDULE);
        node.put(AutomaticUpdateScheduler.P_FUZZY_RECURRENCE, AutomaticUpdateMessages.SchedulerStartup_OnceADay);
    }
}
