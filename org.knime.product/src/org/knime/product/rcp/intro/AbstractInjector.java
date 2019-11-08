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
 *   23.06.2014 (thor): created
 */
package org.knime.product.rcp.intro;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.widgets.Display;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.FileUtil;
import org.w3c.dom.Document;

/**
 * Abstract base class for all injectors that modify the intro page.
 *
 * @author Thorsten Meinl, KNIME AG, Zurich, Switzerland
 */
abstract class AbstractInjector extends AbstractIntroPageModifier implements Runnable {
    private final DocumentBuilderFactory m_parserFactory;

    protected final XPathFactory m_xpathFactory;

    protected final TransformerFactory m_transformerFactory;

    private final ReentrantLock m_introFileLock;

    protected final IEclipsePreferences m_prefs;

    protected final boolean m_isFreshWorkspace;

    /**
     * Creates a new injector.
     *
     * @param templateFile the template file in the temporary directory
     * @param introFileLock lock for the intro file
     * @param preferences the intro page preferences
     * @param isFreshWorkspace <code>true</code> if we are starting in a fresh workspace, <code>false</code> otherwise
     * @param parserFactory a parser factory that will be re-used
     * @param xpathFactory an XPath factory that will be re-used
     * @param transformerFactory a transformer factory that will be re-used
     */
    protected AbstractInjector(final File templateFile, final ReentrantLock introFileLock,
        final IEclipsePreferences preferences, final boolean isFreshWorkspace,
        final DocumentBuilderFactory parserFactory, final XPathFactory xpathFactory,
        final TransformerFactory transformerFactory) {
        super(templateFile);
        m_introFileLock = introFileLock;
        m_prefs = preferences;
        m_isFreshWorkspace = isFreshWorkspace;
        m_parserFactory = parserFactory;
        m_xpathFactory = xpathFactory;
        m_transformerFactory = transformerFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void run() {
        try {
            prepareData();
            m_introFileLock.lock();
            try {
                DocumentBuilder parser = m_parserFactory.newDocumentBuilder();
                parser.setEntityResolver(EmptyDoctypeResolver.INSTANCE);
                Document doc = parser.parse(getIntroPageFile());
                XPath xpath = m_xpathFactory.newXPath();
                injectData(doc, xpath);
                writeFile(doc);
                refreshIntroEditor();
            } finally {
                m_introFileLock.unlock();
            }
        } catch (Exception ex) {
            NodeLogger.getLogger(getClass()).warn("Could not modify intro page: " + ex.getMessage(), ex);
        }
    }

    private void refreshIntroEditor() {
        final Browser browser = findIntroPageBrowser();
        if (browser != null) {
            Display.getDefault().asyncExec(new Runnable() {
                @Override
                public void run() {
                    browser.refresh();
                }
            });
        }
    }

    /**
     * Method that is called before the intro page is locked and read. Subclasses may retrieve remote information to
     * perform other longer-runnning tasks for acquiring information to be injected into the page. The default
     * implementation does nothing.
     *
     * @throws Exception if an error occurs
     */
    protected void prepareData() throws Exception {

    }

    /**
     * Modifies the given intro page document and injects data.
     *
     * @param doc document for the intro page
     * @param xpath an XPath object that can be used to evaluate XPath expressions
     * @throws Exception if an error occurs
     */
    protected abstract void injectData(Document doc, XPath xpath) throws Exception;

    private void writeFile(final Document doc) throws IOException, TransformerException {
        File temp = FileUtil.createTempFile("intro", ".html", true);

        try (OutputStream out = new FileOutputStream(temp)) {
            Transformer serializer = m_transformerFactory.newTransformer();
            serializer.setOutputProperty(OutputKeys.METHOD, "xhtml");
            serializer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "about:legacy-compat");
            serializer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            serializer.transform(new DOMSource(doc), new StreamResult(out));
        }
        Files.move(temp.toPath(), getIntroPageFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
