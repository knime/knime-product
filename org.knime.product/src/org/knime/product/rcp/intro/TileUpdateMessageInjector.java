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
 *   21 Jun 2019 (albrecht): created
 */
package org.knime.product.rcp.intro;

import java.io.File;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.knime.core.eclipseUtil.UpdateChecker.UpdateInfo;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * @author Christian Albrecht, KNIME GmbH, Konstanz, Germany
 */
class TileUpdateMessageInjector extends AbstractInjector {

    private List<UpdateInfo> m_newReleases;
    private List<String> m_bugfixes;

    protected TileUpdateMessageInjector(final File templateFile, final ReentrantLock introFileLock,
        final IEclipsePreferences preferences, final boolean isFreshWorkspace,
        final DocumentBuilderFactory parserFactory, final XPathFactory xpathFactory,
        final TransformerFactory transformerFactory) {
        super(templateFile, introFileLock, preferences, isFreshWorkspace, parserFactory, xpathFactory,
            transformerFactory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void prepareData() throws Exception {
        m_newReleases = UpdateDetector.checkForNewRelease();
        m_bugfixes = UpdateDetector.checkForBugfixes();
    }

    private void injectReleaseTile(final Document doc, final XPath xpath, final boolean bugfix)
        throws XPathExpressionException {
        boolean updatePossible = true;
        String shortName = "";
        if (!bugfix) {
            for (UpdateInfo ui : m_newReleases) {
                updatePossible &= ui.isUpdatePossible();
            }
            shortName = m_newReleases.get(0).getShortName();
        }
        String title = bugfix ? "Updates available" : "Update now to " + shortName;
        // This needs to be dynamically populated, possibly also a new field in the releases.txt
        String tileContent = "Get the latest features and enhancements!";
        if (bugfix) {
            if (m_bugfixes.size() >= 2) {
                tileContent = "There are updates for " + m_bugfixes.size() + " extensions available.";
            } else {
                tileContent = "There is an update for " + m_bugfixes.size() + " extension available.";
            }
        }
        String icon = updatePossible ? "img/update.svg" : "img/arrow-download.svg";
        String action = updatePossible ? "intro://invokeUpdate/" : "https://www.knime.com/downloads?src=knimeapp";
        String buttonText = updatePossible ? "Update now" : "Download now";
        Element updateTile = TileInjector.createUpdateBanner(doc, icon, title, action, buttonText);

        // add update tile as new first tile, remove third tile
        Element contentContainer =
                (Element)xpath.evaluate("//div[@id='content-container']", doc.getDocumentElement(), XPathConstants.NODE);
        Element welcomePageWrapper =
                (Element)xpath.evaluate("//div[@id='welcome-page-wrapper']", doc.getDocumentElement(), XPathConstants.NODE);
        Element titleWrapper =
                (Element)xpath.evaluate("//div[@id='title-wrapper']", doc.getDocumentElement(), XPathConstants.NODE);
        titleWrapper.setAttribute("style", "background: #FFF");
        welcomePageWrapper.insertBefore(updateTile, contentContainer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void injectData(final Document doc, final XPath xpath) throws Exception {
        if (!m_newReleases.isEmpty()) {
            injectReleaseTile(doc, xpath, false);
        } else if (!m_bugfixes.isEmpty()) {
            injectReleaseTile(doc, xpath, true);
        }
    }

}
