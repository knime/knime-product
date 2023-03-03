/*******************************************************************************
 * Copyright (c) 2003, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *
 * Note:
 * This code was copied and slightly adapted by KNIME from Eclipse's
 * IDEApplication because it is not possible to subclass it and use the
 * workspace selection dialog at startup.
 *
 *******************************************************************************/
package org.knime.product.rcp;

import java.awt.EventQueue;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Properties;

import javax.swing.SwingUtilities;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.e4.ui.css.swt.internal.theme.ThemeEngine;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.internal.p2.core.helpers.LogHelper;
import org.eclipse.equinox.internal.p2.core.helpers.ServiceHelper;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.operations.ProvisioningJob;
import org.eclipse.equinox.p2.operations.ProvisioningSession;
import org.eclipse.equinox.p2.operations.UpdateOperation;
import org.eclipse.equinox.p2.ui.ProvisioningUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.internal.ide.ChooseWorkspaceData;
import org.eclipse.ui.internal.ide.ChooseWorkspaceDialog;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.extension.NodeFactoryExtensionManager;
import org.knime.core.node.util.ViewUtils;
import org.knime.core.util.EclipseUtil;
import org.knime.core.util.GUIDeadlockDetector;
import org.knime.core.util.IEarlyStartup;
import org.knime.core.util.MutableBoolean;
import org.knime.product.ProductPlugin;
import org.knime.product.p2.RepositoryUpdater;
import org.knime.product.profiles.ProfileManager;
import org.knime.product.rcp.startup.LongStartupHandler;
import org.knime.product.rcp.startup.WindowsDefenderExceptionHandler;

/**
 * This class controls all aspects of the application's execution.
 */
@SuppressWarnings("restriction")
public class KNIMEApplication implements IApplication {

    private static final String THEME_ID_PREFERENCE_KEY = "themeid";

    private static final String KNIME_THEME_ID = "org.knime.product.theme.knime";

    private static final String KNIME_WEB_UI_THEME_ID = "org.knime.ui.java.theme";

    private static final String JUSTUPDATED = "justUpdated";

    private static final String PERSPECTIVE_SYS_PROP = "perspective";

    private static final String WEB_UI_PERSPECTIVE_ID = "org.knime.ui.java.perspective";

    private static final String CLASSIC_PERSPECTIVE_ID = "org.knime.workbench.ui.ModellerPerspective";

    private boolean m_checkForUpdates = false;

    /**
     * The name of the folder containing metadata information for the workspace.
     */
    public static final String METADATA_FOLDER = ".metadata"; //$NON-NLS-1$

    private static final String VERSION_FILENAME = "version.ini"; //$NON-NLS-1$

    private static final String WORKSPACE_VERSION_KEY =
            "org.eclipse.core.runtime"; //$NON-NLS-1$

    private static final String WORKSPACE_VERSION_VALUE = "1"; //$NON-NLS-1$

    private static final String PROP_EXIT_CODE = "eclipse.exitcode"; //$NON-NLS-1$

    /**
     * A special return code that will be recognized by the launcher and used to
     * restart the workbench.
     */
    private static final Integer EXIT_RELAUNCH = Integer.valueOf(24);

    /**
     * {@inheritDoc}
     */
    @Override
    public Object start(final IApplicationContext appContext) throws Exception {
        IEarlyStartup.executeEarlyStartup(true);

        Display display = createDisplay();

        try {
            // open document listener needs to be registered as first
            // thing to account for open document events during startup
            KNIMEOpenDocumentEventProcessor openDocProcessor = new KNIMEOpenDocumentEventProcessor();
            display.addListener(SWT.OpenDocument, openDocProcessor);
            KNIMEOpenUrlEventProcessor openUrlProcessor = new KNIMEOpenUrlEventProcessor();
            display.addListener(SWT.OpenUrl, openUrlProcessor);

            if (!checkInstanceLocation()) {
                appContext.applicationRunning();
                return EXIT_OK;
            }

            final boolean defenderDialogShown = WindowsDefenderExceptionHandler.getInstance()
                .checkForAndAddExceptionToWindowsDefender("startup-dialog-noshow", display);
            LongStartupHandler.getInstance().onStartupCommenced("startup-dialog-noshow", !defenderDialogShown, display);

            ViewUtils.setLookAndFeel();

            ProfileManager.getInstance().applyProfiles();

            // Load node factories asynchronously because the process is very slow, has to happen after the workspace
            // has been selected because the `NodeLogger` class may be loaded, which needs a workspace to log to.
            // This also initializes KNIMEConstants as early as possible in order to avoid deadlocks during startup.
            KNIMEConstants.GLOBAL_THREAD_POOL.submit(NodeFactoryExtensionManager::getInstance);

            //needs to be called in order to initialize the deprecated KNIMEConstants.KNIME16X16 property
            //need to be lazily (later) invoked, otherwise it will hang on MacOS
            SwingUtilities.invokeLater(KNIMEConstants::getKNIMEIcon16X16);

            parseApplicationArguments(appContext);

            // initialize common classes early in order to avoid deadlocks
            NodeLogger.class.getName();

            RepositoryUpdater.INSTANCE.addDefaultRepositories();
            RepositoryUpdater.INSTANCE.updateArtifactRepositoryURLs();

            updateTheme();

            // Required to make the CEF browser work properly (in particular the so-called 'browser functions' which are
            // used to call java-functions from js).
            // With every CEF update we'll need to check whether this is still necessary.
            if (System.getProperty("chromium.args") == null) {
                System.setProperty("chromium.args", "--disable-web-security");
            }

            final var iniChangedChecker = new IniChangedChecker(!EclipseUtil.isRunFromSDK() ? getIniPath() : null)
                    .digestIni();

            int returnCode;
            if (m_checkForUpdates && checkForUpdates()) {
                returnCode = PlatformUI.RETURN_RESTART;
            } else {
                startDeadlockDetectors(display);

                // create the workbench with this advisor and run it until it exits
                // N.B. createWorkbench remembers the advisor, and also registers the workbench globally so that all UI
                // plug-ins can find it using PlatformUI.getWorkbench() or AbstractUIPlugin.getWorkbench()
                returnCode = PlatformUI.createAndRunWorkbench(display, getWorkbenchAdvisor(openDocProcessor,
                    openUrlProcessor, iniChangedChecker));
            }

            // If the knime.ini did change, we should not restart since it is not re-read at all and critical
            // changes might not be applied, e.g. a jvm change
            if (returnCode == PlatformUI.RETURN_RESTART && iniChangedChecker.iniDidChange()) {
                // The user is notified of the required action using the preShutdown hook in the workbench advisor,
                // where it is possible to show a dialog, since the workbench/display are not yet closed/disposed.
                return EXIT_OK;
            }

            // the workbench doesn't support relaunch yet (bug 61809) so
            // for now restart is used, and exit data properties are checked
            // here to substitute in the relaunch return code if needed
            if (returnCode != PlatformUI.RETURN_RESTART) {
                return EXIT_OK;
            }
            // if the exit code property has been set to the relaunch code, then
            // return that code now, otherwise this is a normal restart
            return EXIT_RELAUNCH.equals(Integer.getInteger(PROP_EXIT_CODE)) ? EXIT_RELAUNCH
                    : EXIT_RESTART;
        } finally {
            if (display != null) {
                try {
                    display.dispose();
                } catch (SWTException e) {
                    // attempt to fix ui hangs on mac when the closes, see AP-8117
                    NodeLogger.getLogger(KNIMEApplication.class).debug(
                        "Exception while disposing Display object: " + e.getMessage(), e);
                }
            }
        }
    }

    /*
     * Prevents the Web UI from being started. Can be removed once the AP is allowed to be started with the new Web UI.
     */
    private static void updateTheme() {
        // Set the theme to the KNIME theme if no theme or the dark theme is configured
        // The dark theme is unusable and we do not allow starting KNIME with the dark theme
        final IEclipsePreferences themeNode = InstanceScope.INSTANCE.getNode(ThemeEngine.THEME_PLUGIN_ID);
        final String themeConfigured = themeNode.get(THEME_ID_PREFERENCE_KEY, null);
        if (themeConfigured == null || themeConfigured.equals(ThemeEngine.E4_DARK_THEME_ID)) {
            themeNode.put(THEME_ID_PREFERENCE_KEY, KNIME_THEME_ID);
        }

        if (System.getProperty(PERSPECTIVE_SYS_PROP) != null) {
            if (isStartedWithWebUI()) {
                // make sure to set the Web UI theme if started with the Web UI
                themeNode.put(THEME_ID_PREFERENCE_KEY, KNIME_WEB_UI_THEME_ID);
            } else if (isStartedWithClassicUI()) {
                // make sure that we don't start with the Web UI theme
                if (themeConfigured != null && themeConfigured.equals(KNIME_WEB_UI_THEME_ID)) {
                    themeNode.put(THEME_ID_PREFERENCE_KEY, KNIME_THEME_ID);
                }
            }
        }
    }

    static boolean isStartedWithWebUI() {
        return WEB_UI_PERSPECTIVE_ID.equals(System.getProperty(PERSPECTIVE_SYS_PROP));
    }

    private static boolean isStartedWithClassicUI() {
        return CLASSIC_PERSPECTIVE_ID.equals(System.getProperty(PERSPECTIVE_SYS_PROP));
    }

    private void parseApplicationArguments(final IApplicationContext context) {
        Object args =
                context.getArguments()
                        .get(IApplicationContext.APPLICATION_ARGS);
        if (args instanceof String[]) {
            String[] stringArgs = (String[])args;
            for (int i = 0; i < stringArgs.length; i++) {
                if ("-checkForUpdates".equals(stringArgs[i])) {
                    m_checkForUpdates = true;
                }
            }
        } else if (args != null) {
            System.err
                    .println("Unable to cast class "
                            + args.getClass().getName()
                            + " to string array, toString() returns "
                            + args.toString());
        }
    }

    private static WorkbenchAdvisor getWorkbenchAdvisor(final KNIMEOpenDocumentEventProcessor openDocProcessor,
        final KNIMEOpenUrlEventProcessor openUrlProcessor, final IniChangedChecker iniChangedChecker) {
        return new KNIMEApplicationWorkbenchAdvisor(openDocProcessor, openUrlProcessor, iniChangedChecker);
    }

    /**
     * Creates the display used by the application.
     *
     * @return the display used by the application
     */
    protected Display createDisplay() {
        return PlatformUI.createDisplay();
    }

    /**
     * Return true if a valid workspace path has been set and false otherwise.
     * Prompt for and set the path if possible and required.
     *
     * @return true if a valid instance location has been set and false
     *         otherwise
     */
    private static boolean checkInstanceLocation() {
        // -data @none was specified but an ide requires workspace
        Location instanceLoc = Platform.getInstanceLocation();
        if (instanceLoc == null) {
            MessageDialog
                    .openError(
                            null,
                            IDEWorkbenchMessages.IDEApplication_workspaceMandatoryTitle,
                            IDEWorkbenchMessages.IDEApplication_workspaceMandatoryMessage);
            return false;
        }

        // -data "/valid/path", workspace already set
        if (instanceLoc.isSet()) {
            // make sure the meta data version is compatible (or the user has
            // chosen to overwrite it).
            if (!checkValidWorkspace(instanceLoc.getURL())) {
                return false;
            }

            // at this point its valid, so try to lock it and update the
            // metadata version information if successful
            try {
                if (instanceLoc.lock()) {
                    writeWorkspaceVersion();
                    return true;
                }

                // we failed to create the directory.
                // Two possibilities:
                // 1. directory is already in use
                // 2. directory could not be created
                File workspaceDirectory =
                        new File(instanceLoc.getURL().getFile());
                if (workspaceDirectory.exists()) {
                    MessageDialog
                            .openError(
                                    null,
                                    IDEWorkbenchMessages.IDEApplication_workspaceCannotLockTitle,
                                    NLS.bind(IDEWorkbenchMessages.IDEApplication_workspaceCannotLockMessage,
                                        workspaceDirectory.getAbsolutePath()));
                } else {
                    MessageDialog
                            .openError(
                                    null,
                                    IDEWorkbenchMessages.IDEApplication_workspaceCannotBeSetTitle,
                                    IDEWorkbenchMessages.IDEApplication_workspaceCannotBeSetMessage);
                }
            } catch (IOException e) {
                MessageDialog.openError(null,
                        IDEWorkbenchMessages.InternalError, e.getMessage());
            }
            return false;
        }

        URL defaultLocation = instanceLoc.getDefault();
        if (Platform.OS_MACOSX.equals(Platform.getOS())) {
            if (defaultLocation.getPath().contains("/Knime.app/")) {
                URL url = Platform.getInstallLocation().getURL();
                try {
                    defaultLocation = new URL(url.getProtocol(), url.getHost(), url.getPath() + "/workspace");
                } catch (MalformedURLException ex) {
                    // should not happen
                }
            }
        }

        // -data @noDefault or -data not specified, prompt and set
        ChooseWorkspaceData launchData = new ChooseWorkspaceData(defaultLocation);

        boolean force = false;
        while (true) {
            URL workspaceUrl = promptForWorkspace(launchData, force);
            if (workspaceUrl == null) {
                return false;
            }

            // if there is an error with the first selection, then force the
            // dialog to open to give the user a chance to correct
            force = true;

            try {
                // the operation will fail if the url is not a valid
                // instance data area, so other checking is unneeded
                if (instanceLoc.set(workspaceUrl, true)) {
                    launchData.writePersistedData();
                    writeWorkspaceVersion();
                    return true;
                }
                // by this point it has been determined that the workspace is
                // already in use -- force the user to choose again
                MessageDialog.openError(null,
                                        IDEWorkbenchMessages.IDEApplication_workspaceInUseTitle,
                                        NLS.bind(IDEWorkbenchMessages.IDEApplication_workspaceInUseMessage, workspaceUrl.getPath()));
            } catch (Exception e) {
                MessageDialog
                        .openError(
                                null,
                                IDEWorkbenchMessages.IDEApplication_workspaceCannotBeSetTitle,
                                IDEWorkbenchMessages.IDEApplication_workspaceCannotBeSetMessage);
            }
        }
    }

    /**
     * Open a workspace selection dialog on the argument shell, populating the
     * argument data with the user's selection. Perform first level validation
     * on the selection by comparing the version information. This method does
     * not examine the runtime state (e.g., is the workspace already locked?).
     *
     * @param shell
     * @param launchData
     * @param force setting to true makes the dialog open regardless of the
     *            showDialog value
     * @return An URL storing the selected workspace or null if the user has
     *         canceled the launch operation.
     */
    private static URL promptForWorkspace(final ChooseWorkspaceData launchData, boolean force) {
        URL url = null;
        do {
            // don't use the parent shell to make the dialog a top-level
            // shell. See bug 84881.
            // workaround for AP-8607: sets the title of the workspace choice dialog
            IDEWorkbenchMessages.ChooseWorkspaceDialog_dialogName = "KNIME Analytics Platform Launcher";
            final ChooseWorkspaceDialog chooseWS = new ChooseWorkspaceDialog(null, launchData, false, true);
            chooseWS.prompt(force);
            String instancePath = launchData.getSelection();
            if (instancePath == null) {
                return null;
            }

            // the dialog is not forced on the first iteration, but is on every
            // subsequent one -- if there was an error then the user needs to be
            // allowed to fix it
            force = true;

            // 70576: don't accept empty input
            if (instancePath.isEmpty()) {
                MessageDialog
                        .openError(
                                null,
                                IDEWorkbenchMessages.IDEApplication_workspaceEmptyTitle,
                                IDEWorkbenchMessages.IDEApplication_workspaceEmptyMessage);
            }
            // create the workspace if it does not already exist
            File workspace = new File(instancePath);
            if (!workspace.exists() && !workspace.mkdir()) {
                // selected fresh workspace not writable
                MessageDialog.openError(null, IDEWorkbenchMessages.IDEApplication_workspaceCannotBeSetTitle,
                                        IDEWorkbenchMessages.IDEApplication_workspaceCannotBeSetMessage);
                continue;
            }
            try {
                // Don't use File.toURL() since it adds a leading slash that
                // Platform does not
                // handle properly. See bug 54081 for more details.
                String path =
                        workspace.getAbsolutePath().replace(File.separatorChar,
                                '/');
                url = new URL("file", null, path); //$NON-NLS-1$
            } catch (MalformedURLException e) {
                MessageDialog
                        .openError(
                                null,
                                IDEWorkbenchMessages.IDEApplication_workspaceInvalidTitle,
                                IDEWorkbenchMessages.IDEApplication_workspaceInvalidMessage);
        		continue;
            }
        } while (!checkValidWorkspace(url));

        return url;
    }

    /**
     * Return true if the argument directory is ok to use as a workspace and
     * false otherwise. A version check will be performed, and a confirmation
     * box may be displayed on the argument shell if an older version is
     * detected.
     *
     * @return true if the argument URL is ok to use as a workspace and false
     *         otherwise.
     */
    private static boolean checkValidWorkspace(final URL url) {
        // a null url is not a valid workspace
        if (url == null) {
            return false;
        }

        String version = readWorkspaceVersion(url);

        // if the version could not be read, then there is not any existing
        // workspace data to trample, e.g., perhaps its a new directory that
        // is just starting to be used as a workspace
        if (version == null) {
            return true;
        }

        final int ide_version = Integer.parseInt(WORKSPACE_VERSION_VALUE);
        int workspace_version = Integer.parseInt(version);

        // equality test is required since any version difference (newer
        // or older) may result in data being trampled
        if (workspace_version == ide_version) {
            return true;
        }

        // At this point workspace has been detected to be from a version
        // other than the current ide version -- find out if the user wants
        // to use it anyhow.
        String title, message;
        if (workspace_version > ide_version) {
            title = IDEWorkbenchMessages.IDEApplication_versionTitle_newerWorkspace;
            message = NLS.bind(IDEWorkbenchMessages.IDEApplication_versionMessage_newerWorkspace, url.getFile());
        } else {
            title = IDEWorkbenchMessages.IDEApplication_versionTitle_olderWorkspace;
            message = NLS.bind(IDEWorkbenchMessages.IDEApplication_versionMessage_olderWorkspace, url.getFile());
        }

        return MessageDialog.openConfirm(null, title, message);
    }

    /**
     * Look at the argument URL for the workspace's version information. Return
     * that version if found and null otherwise.
     */
    private static String readWorkspaceVersion(final URL workspace) {
        File versionFile = getVersionFile(workspace, false);
        if (versionFile == null || !versionFile.exists()) {
            return null;
        }

        try {
            // Although the version file is not spec'ed to be a Java properties
            // file, it happens to follow the same format currently, so using
            // Properties to read it is convenient.
            Properties props = new Properties();
            try (FileInputStream is = new FileInputStream(versionFile)) {
                props.load(is);
            }

            return props.getProperty(WORKSPACE_VERSION_KEY);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Write the version of the metadata into a known file overwriting any
     * existing file contents. Writing the version file isn't really crucial, so
     * the function is silent about failure
     */
    private static void writeWorkspaceVersion() {
        Location instanceLoc = Platform.getInstanceLocation();
        if (instanceLoc == null || instanceLoc.isReadOnly()) {
            return;
        }

        File versionFile = getVersionFile(instanceLoc.getURL(), true);
        if (versionFile == null) {
            return;
        }

        OutputStream output = null;
        try {
            String versionLine =
                    WORKSPACE_VERSION_KEY + '=' + WORKSPACE_VERSION_VALUE;

            output = new FileOutputStream(versionFile);
            output.write(versionLine.getBytes("UTF-8")); //$NON-NLS-1$
        } catch (IOException e) {
        } finally {
            try {
                if (output != null) {
                    output.close();
                }
            } catch (IOException e) {
                // do nothing
            }
        }
    }

    /**
     * The version file is stored in the metadata area of the workspace. This
     * method returns an URL to the file or null if the directory or file does
     * not exist (and the create parameter is false).
     *
     * @param create If the directory and file does not exist this parameter
     *            controls whether it will be created.
     * @return An url to the file or null if the version file does not exist or
     *         could not be created.
     */
    private static File getVersionFile(final URL workspaceUrl,
            final boolean create) {
        if (workspaceUrl == null) {
            return null;
        }

        try {
            // make sure the directory exists
            File metaDir = new File(workspaceUrl.getPath(), METADATA_FOLDER);
            if (!metaDir.exists() && (!create || !metaDir.mkdir())) {
                return null;
            }

            // make sure the file exists
            File versionFile = new File(metaDir, VERSION_FILENAME);
            if (!versionFile.exists()
                    && (!create || !versionFile.createNewFile())) {
                return null;
            }

            return versionFile;
        } catch (IOException e) {
            // cannot log because instance area has not been set
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        final IWorkbench workbench = PlatformUI.getWorkbench();
        if (workbench == null) {
            return;
        }
        final Display display = workbench.getDisplay();
        display.syncExec(new Runnable() {
            @Override
            public void run() {
                if (!display.isDisposed()) {
                    workbench.close();
                }
            }
        });
    }

    private static boolean checkForUpdates() {
        final IPreferenceStore prefStore =
                ProductPlugin.getDefault().getPreferenceStore();
        if (prefStore.getBoolean(JUSTUPDATED)) {
            prefStore.setValue(JUSTUPDATED, false);
            return false;
        }

        final ProvisioningUI provUI = ProvisioningUI.getDefaultUI();
        if (provUI.getRepositoryTracker() == null) {
            LogHelper.log(new Status(IStatus.WARNING, ProductPlugin
                    .getDefault().getBundle().getSymbolicName(),
                    "Updating is not possible of KNIME is started from "
                            + "within the IDE."));
            return false;
        }

        final IProvisioningAgent agent =
                (IProvisioningAgent)ServiceHelper.getService(ProductPlugin
                        .getDefault().getBundle().getBundleContext(),
                        IProvisioningAgent.SERVICE_NAME);
        if (agent == null) {
            LogHelper.log(new Status(IStatus.ERROR, ProductPlugin.getDefault()
                    .getBundle().getSymbolicName(),
                    "No provisioning agent found. This application is "
                            + "not set up for updates."));
        }

        final MutableBoolean restart = new MutableBoolean(false);
        IRunnableWithProgress runnable = new IRunnableWithProgress() {
            @Override
            public void run(final IProgressMonitor monitor)
                    throws InvocationTargetException, InterruptedException {
                ProvisioningSession session = new ProvisioningSession(agent);
                UpdateOperation operation = new UpdateOperation(session);
                SubMonitor sub =
                        SubMonitor.convert(monitor,
                                "Checking for application updates...", 200);
                IStatus status = operation.resolveModal(sub.newChild(100));
                if (status.getSeverity() == IStatus.CANCEL) {
                    throw new OperationCanceledException();
                } else if (status.getSeverity() != IStatus.ERROR) {
                    ProvisioningJob job = operation.getProvisioningJob(null);
                    status = job.runModal(sub.newChild(100));
                    if (status.getSeverity() == IStatus.CANCEL) {
                        throw new OperationCanceledException();
                    }
                    if (status.getCode() == UpdateOperation.STATUS_NOTHING_TO_UPDATE) {
                        // ok, just proceed
                        LogHelper.log(new Status(IStatus.INFO, ProductPlugin
                                .getDefault().getBundle().getSymbolicName(),
                                "No updates found."));
                    } else if (status.getSeverity() != IStatus.ERROR) {
                        prefStore.setValue(JUSTUPDATED, true);
                        restart.setValue(true);
                    } else {
                        LogHelper.log(status);
                    }
                } else {
                    LogHelper.log(status);
                }
            }
        };
        try {
            new ProgressMonitorDialog(null).run(true, true, runnable);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
        }
        return restart.booleanValue();
    }

    private static void startDeadlockDetectors(final Display display) {
        new GUIDeadlockDetector() {
            @Override
            protected String getThreadName() {
                return "SWT Display thread";
            }

            @Override
            protected void enqueue(final Runnable r) {
                display.asyncExec(r);
            }
        };

        new GUIDeadlockDetector() {
            @Override
            protected String getThreadName() {
                return "AWT Event Queue";
            }

            @Override
            protected void enqueue(final Runnable r) {
                EventQueue.invokeLater(r);
            }
        };
    }

    private static Path getIniPath() {
        final var launcherPath = Path.of(System.getProperty("eclipse.launcher"));
        if (Platform.OS_MACOSX.equals(Platform.getOS())) {
            return launcherPath.getParent().resolveSibling("Eclipse").resolve("knime.ini");
        } else {
            return launcherPath.resolveSibling("knime.ini");
        }
    }
}
