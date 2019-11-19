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
 *   Oct 9, 2019 (Daniel Bogenrieder): created
 */
package org.knime.product.rcp.intro;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONObject;
import org.knime.core.eclipseUtil.UpdateChecker.UpdateInfo;
import org.knime.core.node.NodeLogger;

/**
 *
 * @author Christian Albrecht, KNIME GmbH, Konstanz, Germany
 * @since 4.1
 */
class ReleaseMessageUpdater extends AbstractUpdater {

    private List<UpdateInfo> m_newReleases = new ArrayList<UpdateInfo>(0);
    private List<String> m_bugfixes = new ArrayList<String>(0);

    protected ReleaseMessageUpdater(final File introPageFile, final ReentrantLock introFileLock) {
        super(introPageFile, introFileLock);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void updateData() throws Exception{
        if (!m_newReleases.isEmpty()) {
            injectReleaseTile(false);
        } else if (!m_bugfixes.isEmpty()) {
            injectReleaseTile(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void prepareData() throws Exception {
        try {
            m_newReleases = UpdateDetector.checkForNewRelease();
            m_bugfixes = UpdateDetector.checkForBugfixes();
        } catch (Exception e) {
            // offline or server not reachable
            NodeLogger.getLogger(ReleaseMessageUpdater.class)
                .info("Could not check for updates or new releases, possibly offline.");
        }
        if (IntroPage.MOCK_INTRO_PAGE) {
            Thread.sleep(2000);
            if (m_bugfixes.isEmpty()) {
                m_bugfixes.add("Important bugfix");
                m_bugfixes.add("Second bugfix");
            }
            if (m_newReleases.isEmpty()) {
                m_newReleases
                    .add(new UpdateInfo(new URI("https://kni.me"), "KNIME Analytics Platform 5.6", "5.6", true));
            }
        }
    }

    private void injectReleaseTile(final boolean bugfix) {
            boolean updatePossible = true;
            String shortName = "";
            if (!bugfix) {
                for (UpdateInfo ui : m_newReleases) {
                    updatePossible &= ui.isUpdatePossible();
                }
                shortName = m_newReleases.get(0).getShortName();
            }
            String title = "Update now to " + shortName;
            // This needs to be dynamically populated, possibly also a new field in the releases.txt
            String tileContent = bugfix ? "" : "Get the latest features and enhancements!";
            if (bugfix) {
                if (m_bugfixes.size() >= 2) {
                    title = "There are updates for " + m_bugfixes.size() + " extensions available.";
                } else {
                    title = "There is an update for " + m_bugfixes.size() + " extension available.";
                }
            }
            String icon = updatePossible ? "img/update.svg" : "img/arrow-download.svg";
            String action = updatePossible ? "intro://invokeUpdate/" : "https://www.knime.com/downloads?src=knimeapp";
            String buttonText = updatePossible ? "Update now" : "Download now";
            createUpdateBanner(icon, title, tileContent, action, buttonText);
        }

    private void createUpdateBanner(final String icon, final String title, final String tileContent, final String action,
        final String buttonText) {
        JSONObject updateTile = new JSONObject();
        updateTile.put("icon", icon);
        updateTile.put("title", title);
        updateTile.put("tileContent", tileContent);
        updateTile.put("buttonAction", action);
        updateTile.put("buttonText", buttonText);
        executeUpdateInBrowser("displayUpdateTile(" + updateTile + ");");
    }

}
