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
 * ---------------------------------------------------------------------
 *
 * History
 *   24.03.2014 (thor): created
 */
package org.knime.product.rcp.intro;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.internal.browser.SystemBrowserInstance;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.EclipseUtil;
import org.knime.core.util.FileUtil;
import org.knime.product.customizations.ICustomizationService;
import org.knime.workbench.editor2.WorkflowEditor;
import org.knime.workbench.explorer.ExplorerMountTable;
import org.knime.workbench.explorer.filesystem.AbstractExplorerFileStore;
import org.knime.workbench.explorer.view.AbstractContentProvider;
import org.knime.workbench.explorer.view.ContentDelegator;
import org.knime.workbench.explorer.view.ExplorerView;
import org.knime.workbench.explorer.view.actions.NewWorkflowWizard;
import org.knime.workbench.explorer.view.actions.NewWorkflowWizardPage;
import org.knime.workbench.explorer.view.preferences.EditMountPointDialog;
import org.knime.workbench.explorer.view.preferences.MountSettings;
import org.knime.workbench.ui.KNIMEUIPlugin;
import org.knime.workbench.ui.hub.HubLinkHandlerExtension;
import org.knime.workbench.ui.p2.actions.InvokeInstallSiteAction;
import org.knime.workbench.ui.p2.actions.InvokeUpdateAction;
import org.knime.workbench.ui.preferences.PreferenceConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.prefs.BackingStoreException;
import org.xml.sax.SAXException;

/**
 * Class for showing and handling events in the intro page.
 *
 * @author Thorsten Meinl, KNIME AG, Zurich, Switzerland
 * @since 2.10
 */
@SuppressWarnings("restriction")
public class IntroPage implements LocationListener {

    static final boolean MOCK_INTRO_PAGE = Boolean.getBoolean("knime.intro.mock");

    static final boolean DEBUG = EclipseUtil.isRunInDebug();

    private static final NodeLogger LOGGER = NodeLogger.getLogger(IntroPage.class);

    /**
     * Singleton instance.
     */
    public static final IntroPage INSTANCE = new IntroPage();

    private boolean m_freshWorkspace;

    private XPathFactory m_xpathFactory;

    private DocumentBuilderFactory m_parserFactory;

    private TransformerFactory m_transformerFactory;

    private File m_introFile;

    private final ReentrantLock m_lock;

    private final IEclipsePreferences m_prefs =
        InstanceScope.INSTANCE.getNode(FrameworkUtil.getBundle(getClass()).getSymbolicName());

    static Path getWorkbenchStateFile() {
        Bundle myself = FrameworkUtil.getBundle(IntroPage.class);
        IPath path = Platform.getStateLocation(myself);
        return path.toFile().toPath().getParent().resolve("org.eclipse.e4.workbench").resolve("workbench.xmi");
    }

    private IntroPage() {
        // in fresh workspaces, workbench.xmi does not exist yet
        m_freshWorkspace = !Files.exists(getWorkbenchStateFile());

        // workaround for a bug in XPathFinderFactory on MacOS X
        final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        try {
            m_xpathFactory = XPathFactory.newInstance();
            m_parserFactory = DocumentBuilderFactory.newInstance();
            m_parserFactory.setValidating(false);
            m_transformerFactory = TransformerFactory.newInstance();
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
        m_lock = new ReentrantLock();
        injectTiles(false);
    }

    private void injectTiles(final boolean refresh) {
        try {
            String introFile = "intro4.0/intro.xhtml";
            m_introFile = copyTemplate(introFile, refresh);

            BaseInjector baseInjector = new BaseInjector(m_introFile, m_lock, m_prefs, m_freshWorkspace,
                m_parserFactory, m_xpathFactory, m_transformerFactory);
            baseInjector.run();

            updateTiles();

        } catch (IOException | ParserConfigurationException | SAXException | XPathExpressionException
                | TransformerFactoryConfigurationError | TransformerException ex) {
            LOGGER.error("Could not copy welcome page: " + ex.getMessage(), ex);
        }
    }

    private void updateTiles() {
        try {
            Map<String, String> customizationInfo = getBrandingInfo();
            KNIMEConstants.GLOBAL_THREAD_POOL
                .submit(new TileUpdater(m_introFile, m_lock, m_freshWorkspace, customizationInfo));
            KNIMEConstants.GLOBAL_THREAD_POOL.submit(new ReleaseMessageUpdater(m_introFile, m_lock));
        } catch (InterruptedException ex) {
            // should not happen
            LOGGER.info("Got interrupted while submitting injector: " + ex.getMessage(), ex);
        }
    }

    private static Map<String, String> getBrandingInfo() {
        //retrieve the customization information from the service
        BundleContext context = FrameworkUtil.getBundle(IntroPage.class).getBundleContext();
        ServiceReference<?> serviceReference = context.getServiceReference(ICustomizationService.class.getName());
        if (serviceReference == null) {
            return Collections.emptyMap();
        }

        try {
            ICustomizationService service = (ICustomizationService)context.getService(serviceReference);
            if (service == null) {
                return Collections.emptyMap();
            } else if ("com.knime.customizations.CustomizationService".equals(service.getClass().getName())) {
                return service.getCustomizationInfo();
            } else {
                return Collections.emptyMap();
            }
        } finally {
            context.ungetService(serviceReference);
        }
    }

    /**
     * Copies one of the template files into a temporary directory.
     *
     * @param templateFile the template file that should be copied
     * @return the modified temporary file
     */
    private File copyTemplate(final String templateFile, final boolean replaceExisting)
        throws IOException, ParserConfigurationException, SAXException, XPathExpressionException,
        TransformerFactoryConfigurationError, TransformerException {
        File tempTemplate = m_introFile;
        if (!replaceExisting) {
            tempTemplate = FileUtil.createTempFile("intro", ".html", true);
        }
        Bundle myBundle = FrameworkUtil.getBundle(getClass());
        URL introUrl = myBundle.getEntry(templateFile);
        try (InputStream is = introUrl.openStream()) {
            Files.copy(is, tempTemplate.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return tempTemplate;
    }

    /**
     * Shows the intro page.
     */
    public void show() {
        if (m_introFile != null) {
            try {
                // NB we set the AS_EDITOR style and the org.eclipse.help.ui
                // browser id here, to force the welcome page to open in the
                // internal browser. Otherwise it would default to the external
                // browser in eclipse 22-03
                int style = 1 << 5; // AS_EDITOR, see IWorkbenchBrowserSupport
                IWebBrowser browser = PlatformUI.getWorkbench().getBrowserSupport().createBrowser(style,
                    "org.eclipse.help.ui", null, null);
                if (browser instanceof SystemBrowserInstance) {
                    showMissingBrowserWarning();
                } else {
                    browser.openURL(m_introFile.toURI().toURL());
                    updateTiles();
                }
            } catch (PartInitException ex) {
                LOGGER.error("Could not open web browser with first intro page: " + ex.getMessage(), ex);
            } catch (IOException ex) {
                LOGGER.warn("Could not prepare first intro page: " + ex.getMessage(), ex);
            }
        }

        attachLocationListener();
    }

    private static void showMissingBrowserWarning() {
        IPersistentPreferenceStore prefStore =
            (IPersistentPreferenceStore)KNIMEUIPlugin.getDefault().getPreferenceStore();
        boolean omitWarnings = prefStore.getBoolean(PreferenceConstants.P_OMIT_MISSING_BROWSER_WARNING);
        if (!omitWarnings) {
            MessageDialogWithToggle dialog = MessageDialogWithToggle.openWarning(Display.getDefault().getActiveShell(),
                "Missing browser integration",
                "KNIME is unable to display web pages in an internal browser. This may be caused by missing "
                    + "system libraries. Please visit https://www.knime.com/faq#q6 for details.\n"
                    + "Some web pages may open in an external browser.",
                "Do not show again", false, prefStore, PreferenceConstants.P_OMIT_MISSING_BROWSER_WARNING);
            if (dialog.getToggleState()) {
                prefStore.setValue(PreferenceConstants.P_OMIT_MISSING_BROWSER_WARNING, true);
                try {
                    prefStore.save();
                } catch (IOException ex) {
                    // too bad, ignore it
                }
            }
        }
    }

    /**
     * Looks for the open intro page editor (and HTML editor) any tries to attach a location listener. This
     * (unfortunately) involves some heavy reflection stuff as there is no other way to attach a listener otherwise.
     */
    private void attachLocationListener() {
        Browser browser = AbstractIntroPageModifier.findIntroPageBrowser(m_introFile);
        if (browser != null) {
            browser.removeLocationListener(this);
            browser.addLocationListener(this);
        }
    }

    /**
     * Returns whether we have a fresh or already used workspace.
     *
     * @return <code>true</code> if we started with a fresh workspace, <code>false</code> otherwise
     */
    public boolean isFreshWorkspace() {
        return m_freshWorkspace;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changing(final LocationEvent event) {
        try {
            URI uri = new URI(event.location);
            URL welcomeURL = m_introFile.toURI().toURL();
            try {
                if (uri.toURL().getPath().equals(welcomeURL.getPath())) {
                    return;
                }
            } catch (MalformedURLException e) {
                // this will be thrown by intro:// links, handled further down
            }

            if ("intro".equals(uri.getScheme())) {
                handleIntroCommand(uri);
            } else {
                handleLink(uri);
            }
            event.doit = false;
        } catch (URISyntaxException | MalformedURLException ex) {
            LOGGER.error("Invalid URI '" + event.location + "': " + ex.getMessage(), ex);
        }
    }

    private static void handleLink(final URI link) {
        if (HubLinkHandlerExtension.handleLink(link.toString())) {
            return;
        }
        try {
            URL url = link.toURL();
            Program.launch(url.toString());
        } catch (MalformedURLException ex) {
            LOGGER.error("Malformed URL '" + link.toString() + "': " + ex.getMessage(), ex);
        }
    }

    private void handleIntroCommand(final URI command) {
        switch (command.getHost().toLowerCase()) {
            case "openworkflow":
                String workflowUri = command.getPath();
                if (workflowUri.startsWith("/")) {
                    workflowUri = workflowUri.substring(1);
                }
                try {
                    URL u = new URL(workflowUri);
                    IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
                        new URI(u.getProtocol(), u.getHost(), u.getPath(), u.getQuery()), WorkflowEditor.ID, true);
                } catch (URISyntaxException | MalformedURLException ex) {
                    LOGGER.error("Invalid workflow URI '" + workflowUri + "': " + ex.getMessage(), ex);
                } catch (PartInitException ex) {
                    LOGGER.error("Could not open workflow '" + workflowUri + "': " + ex.getMessage(), ex);
                }
                break;
            case "invokeupdate":
                new InvokeUpdateAction().run();
                break;
            case "newworkflow":
                newWorkflow();
                break;
            case "installextensions":
                new InvokeInstallSiteAction().run();
                break;
            case "closeintro":
                closeIntro();
                break;
            case "browseexamples":
                browseExamples();
                break;
            case "setproperty":
                setIntroProperty(command);
                break;
            case "mountserver":
                mountServer();
                break;
            default:
                LOGGER.coding("Unknown intro command: " + command.getHost());
        }
    }

    private void setIntroProperty(final URI command) {
        for (NameValuePair param : URLEncodedUtils.parse(command, Charset.forName("UTF-8"))) {
            m_prefs.putBoolean("org.knime.product.intro." + param.getName(), Boolean.parseBoolean(param.getValue()));
        }
    }

    @SuppressWarnings("unchecked")
    private static void browseExamples() {
        Bundle serverBundle = Platform.getBundle("com.knime.explorer.server");
        if (serverBundle != null) {
            Class<Action> clazz;
            try {
                clazz = (Class<Action>)serverBundle
                    .loadClass("com.knime.explorer.server.internal.ExampleServerLoginAction");
                Action action = clazz.newInstance();
                action.run();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                LOGGER.error("Could not browse example workflow: " + ex.getMessage(), ex);
            }
        }
    }

    private static void newWorkflow() {
        ExplorerView explorerView = null;
        for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
            for (IWorkbenchPage page : window.getPages()) {
                for (IViewReference ref : page.getViewReferences()) {
                    if (ExplorerView.ID.equals(ref.getId())) {
                        explorerView = (ExplorerView)ref.getPart(true);
                        break;
                    }
                }
            }
        }

        Shell shell = PlatformUI.getWorkbench().getDisplay().getActiveShell();
        NewWorkflowWizard newWiz = new NewWorkflowWizard();
        newWiz.init(PlatformUI.getWorkbench(), null);

        WizardDialog dialog = new WizardDialog(shell, newWiz);
        dialog.create();
        dialog.getShell().setText("Create new workflow");
        dialog.getShell().setSize(Math.max(470, dialog.getShell().getSize().x), 350);
        int ok = dialog.open();
        if ((ok == Window.OK) && (explorerView != null)) {
            // update the tree
            IWizardPage currentPage = dialog.getCurrentPage();
            if (currentPage instanceof NewWorkflowWizardPage) {
                NewWorkflowWizardPage nwwp = (NewWorkflowWizardPage)currentPage;
                AbstractExplorerFileStore file = nwwp.getNewFile();
                Object p = ContentDelegator.getTreeObjectFor(file.getParent());
                explorerView.setNextSelection(file);
                explorerView.getViewer().refresh(p);
            }
        }
    }

    private void closeIntro() {
        for (IEditorReference ref : PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
            .getEditorReferences()) {
            try {
                if (AbstractIntroPageModifier.isIntroPageEditor(ref, m_introFile)) {
                    PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
                        .closeEditor(ref.getEditor(false), false);
                }
            } catch (PartInitException ex) {
                LOGGER.error("Could not close intro page: " + ex.getMessage(), ex);
            }
        }
    }

    private static void mountServer() {
        // AP-8989 switching to IEclipsePreferences
        List<MountSettings> mountSettingsList = new ArrayList<>();

        try {
            mountSettingsList = MountSettings.loadSortedMountSettingsFromPreferences();
        } catch (BackingStoreException e) {
            LOGGER.error(e.getMessage(), e);
        }

        Set<String> idSet = new LinkedHashSet<>();
        for (MountSettings settings : mountSettingsList) {
            idSet.add(settings.getFactoryID());
        }
        List<String> contentProviderIDs = new ArrayList<String>(idSet);

        List<String> mountIDs = new ArrayList<String>(mountSettingsList.size());
        for (MountSettings settings : mountSettingsList) {
            mountIDs.add(settings.getMountID());
        }

        EditMountPointDialog dlg = new EditMountPointDialog(Display.getDefault().getActiveShell(),
            ExplorerMountTable.getAddableContentProviders(contentProviderIDs), mountIDs);
        if (dlg.open() != Window.OK) {
            return;
        }
        AbstractContentProvider newCP = dlg.getContentProvider();
        if (newCP != null) {
            MountSettings mountSettings = new MountSettings(newCP);
            if (mountSettings.getDefaultMountID() == null) {
                mountSettings.setDefaultMountID(dlg.getDefaultMountID());
            }
            mountSettingsList.add(mountSettings);

            //store new mount point settings
            MountSettings.saveMountSettings(mountSettingsList);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changed(final LocationEvent event) {
        // nothing to do here
    }
}
