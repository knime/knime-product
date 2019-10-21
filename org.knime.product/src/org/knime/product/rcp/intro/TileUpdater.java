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
 *   Oct 7, 2019 (Daniel Bogenrieder): created
 */
package org.knime.product.rcp.intro;

import java.io.File;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.transform.TransformerFactory;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.widgets.Display;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Daniel Bogenrieder, KNIME GmbH, Konstanz, Germany
 */
public class TileUpdater extends AbstractUpdater {

    private static final String EXAMPLE_WORKFLOW_URI =
            "knime://LOCAL/Example%20Workflows/Basic%20Examples/Visual%20Analysis%20of%20Sales%20Data/workflow.knime";

    private static File m_templateFile;
    /**
     * @param templateFile
     * @param introFileLock
     * @param preferences
     * @param isFreshWorkspace
     * @param transformerFactory
     */
    protected TileUpdater(final File templateFile, final ReentrantLock introFileLock,
        final IEclipsePreferences preferences, final boolean isFreshWorkspace,
        final TransformerFactory transformerFactory) {
        super(templateFile, introFileLock, preferences, isFreshWorkspace,
            transformerFactory);
        m_templateFile = templateFile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void prepareData() throws Exception {
            Thread.sleep(2000);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void updateData() {
        JSONObject firstTile;
        JSONObject secondTile;
        JSONObject thirdTile;
        if (m_isFreshWorkspace) {
            firstTile = createOpenExampleWorkflowTile();
            secondTile = createHubTile();
            thirdTile = createIntroMailTile();
            updateTiles(firstTile, secondTile, thirdTile);
            hideElement("hub-search-bar");
        } else {
            firstTile = createRegisterHubTile();
            secondTile = createCoursesTile();
            thirdTile = createForumTile();
            updateTiles(firstTile, secondTile, thirdTile);
        }

    }


    private static JSONObject createOpenExampleWorkflowTile() {
        JSONObject tile = createTile("img/workflow.svg", "Example", "Get started with this example", "",
            "intro://openworkflow/" + EXAMPLE_WORKFLOW_URI, "Open workflow");
        return tile;
    }

    private static JSONObject createHubTile() {
        JSONObject tile = createTile("img/screen-perspective-hub.jpg", "Hub", "Looking for more examples? Visit the KNIME Hub", "",
            "https://hub.knime.com?src=knimeapp", "KNIME Hub");
        return tile;
    }

    private static JSONObject createIntroMailTile() {
        JSONObject tile = createTitleTile("img/mail.svg", "Getting started", "Sign up for introductory emails", "",
            "These messages will get you up and running as quickly as possible.",
            "https://www.knime.com/form/help-getting-started?src=knimeapp", "Sign up");
        return tile;
    }

    private static JSONObject createRegisterHubTile() {
        JSONObject tile =  createTile("img/hub-connect.png", "Hub", "Share your workflows and components on KNIME Hub", "You are now able to share your workflow on our newly integrated hub platform",
            "https://hub.knime.com/site/about?src=knimeapp", "Learn more");
        return tile;
    }

    private static JSONObject createCoursesTile() {
        JSONObject tile = createTile("img/courses.svg", "Courses","KNIME Courses: learn all about Big Data, Text Mining and more", "",
            "https://www.knime.com/courses?src=knimeapp", "Explore KNIME Courses");
        return tile;
    }

    private static JSONObject createForumTile() {
       JSONObject tile = createTile("img/pic-community.jpg","Community" ,"Questions? Ask the community", "",
            "https://forum.knime.com?src=knimeapp", "Visit Forum");
       return tile;
    }

    private static void hideElement(final String id) {
        Browser browser = findIntroPageBrowser(m_templateFile);
        if (browser != null) {
            Display.getDefault().asyncExec(new Runnable() {
                @Override
                public void run() {
                    browser.execute("hideElement('" + id + "');");
                }
            });
        }
    }

    static JSONObject createTitleTile(final String imageLocation, final String tileText, final String tileSubText,
        final String titleText, final String contentText, final String buttonAction, final String buttonText) {
        JSONObject tileDetails = new JSONObject();
        return tileDetails;
    }

    static JSONObject createTile(final String imageLocation, final String tagText, final String titleText,
        final String subTitleText, final String buttonAction, final String buttonText) {
        JSONObject tileDetails = new JSONObject();
        tileDetails.put("imageLocation", imageLocation);
        tileDetails.put("tagText", tagText);
        tileDetails.put("titleText", titleText);
        tileDetails.put("subTitleText", subTitleText);
        tileDetails.put("buttonAction", buttonAction);
        tileDetails.put("buttonText", buttonText);
        return tileDetails;
    }

    static void updateTiles(final JSONObject firstTile, final JSONObject secondTile, final JSONObject thirdTile) {
        JSONArray tilesArray = new JSONArray();
        tilesArray.put(firstTile);
        tilesArray.put(secondTile);
        tilesArray.put(thirdTile);
        Browser browser = findIntroPageBrowser(m_templateFile);
        if (browser != null) {
            Display.getDefault().asyncExec(new Runnable() {
                @Override
                public void run() {
                    browser.execute("updateTile(" + tilesArray + ");");
                }
            });
        }
    }

    static void createUpdateBanner(final String icon, final String title, final String tileContent, final String action, final String buttonText) {
        JSONObject updateTile = new JSONObject();
        updateTile.put("icon", icon);
        updateTile.put("title", title);
        updateTile.put("tileContent", tileContent);
        updateTile.put("buttonAction", action);
        updateTile.put("buttonText", buttonText);
        Browser browser = findIntroPageBrowser(m_templateFile);
        if (browser != null) {
            Display.getDefault().asyncExec(new Runnable() {
                @Override
                public void run() {
                    browser.execute("displayUpadteTile(" + updateTile + ");");
                }
            });
        }

    }

}
