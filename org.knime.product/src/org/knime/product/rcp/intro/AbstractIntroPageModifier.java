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
 *   6 Nov 2019 (albrecht): created
 */
package org.knime.product.rcp.intro;

import java.io.File;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.core.runtime.AssertionFailedException;
import org.eclipse.swt.browser.Browser;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.browser.BrowserViewer;
import org.eclipse.ui.internal.browser.WebBrowserEditor;
import org.eclipse.ui.internal.browser.WebBrowserEditorInput;
import org.knime.core.node.NodeLogger;

/**
 *
 * @author Christian Albrecht, KNIME GmbH, Konstanz, Germany
 */
public class AbstractIntroPageModifier {

    private final File m_introPageFile;

    /**
     * Creates a new modifier for the intro page
     *
     * @param introPageFile the intro page file in the temporary directory
     */
    public AbstractIntroPageModifier(final File introPageFile) {
        m_introPageFile = introPageFile;
    }

    /**
     * @return the introPageFile
     */
    public File getIntroPageFile() {
        return m_introPageFile;
    }

    /**
     * Looks for the open intro page editor (and HTML editor) and returns the Browser instance. This (unfortunately)
     * involves some heavy reflection stuff as there is no other way to attach a listener otherwise. If the intro page
     * editor cannot be found then <code>null</code> is returned.
     *
     * @return the browser instance showing the intro page or <code>null</code>
     */
    protected Browser findIntroPageBrowser() {
        return findIntroPageBrowser(m_introPageFile);
    }

    /**
     * Looks for the open intro page editor (and HTML editor) and returns the Browser instance. This (unfortunately)
     * involves some heavy reflection stuff as there is no other way to attach a listener otherwise. If the intro page
     * editor cannot be found then <code>null</code> is returned.
     *
     * @param introPageFile the temporary intro page file
     * @return the browser instance showing the intro page or <code>null</code>
     */
    @SuppressWarnings("restriction")
    static Browser findIntroPageBrowser(final File introPageFile) {
        for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
            for (IWorkbenchPage page : window.getPages()) {
                for (IEditorReference ref : page.getEditorReferences()) {
                    try {
                        if (isIntroPageEditor(ref, introPageFile)) {
                            IEditorPart part = ref.getEditor(false);
                            if (part instanceof WebBrowserEditor) {
                                WebBrowserEditor editor = (WebBrowserEditor)part;

                                Field webBrowser = editor.getClass().getDeclaredField("webBrowser");
                                webBrowser.setAccessible(true);
                                BrowserViewer viewer = (BrowserViewer)webBrowser.get(editor);

                                Field browserField = viewer.getClass().getDeclaredField("browser");
                                browserField.setAccessible(true);
                                return (Browser)browserField.get(viewer);
                            }
                        }
                    } catch (PartInitException ex) {
                        NodeLogger.getLogger(AbstractInjector.class).error(
                            "Could not open web browser with intro page: " + ex.getMessage(), ex);
                    } catch (SecurityException | NoSuchFieldException | IllegalArgumentException
                            | IllegalAccessException ex) {
                        NodeLogger.getLogger(AbstractInjector.class).error(
                            "Could not attach location listener to web browser: " + ex.getMessage(), ex);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns whether the given editor is an intro editor. This is checked by looking the URL the editor displays.
     *
     * @param ref an editor reference
     * @param introPageFile the temporary intro page file
     * @return <code>true</code> if it is an intro page editor, <code>false</code> otherwise
     * @throws PartInitException if there was an error restoring the editor input
     */
    @SuppressWarnings("restriction")
    static boolean isIntroPageEditor(final IEditorReference ref, final File introPageFile) throws PartInitException {
        if (introPageFile == null) {
            return false;
        }

        try {
            URL expectedURL = introPageFile.toURI().toURL();
            IEditorInput input = ref.getEditorInput();
            return (input instanceof WebBrowserEditorInput)
                && ((WebBrowserEditorInput)input).getURL().getPath()
                    .equals(expectedURL.getPath());
        } catch (AssertionFailedException ex) {
            // may happen if the editor "ref" points to a resource that doesn't exist any more
            NodeLogger
                .getLogger(AbstractInjector.class)
                .error(
                    "Could not get editor input, probably the resource was removed outside Eclipse: " + ex.getMessage(),
                    ex);
            return false;
        } catch (MalformedURLException e) {
            NodeLogger.getLogger(AbstractInjector.class).error("Invalid welcome page URL " + e.getMessage());
            return false;
        }
    }

}
