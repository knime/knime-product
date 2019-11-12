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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.Platform;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeLogger;
import org.knime.product.rcp.intro.json.JSONCategory;
import org.knime.product.rcp.intro.json.JSONTile;
import org.knime.product.rcp.intro.json.OfflineJsonCollector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 *
 * @author Daniel Bogenrieder, KNIME GmbH, Konstanz, Germany
 */
public class TileUpdater extends AbstractUpdater {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(TileUpdater.class);
    private static final String WELCOME_PAGE_ENDPOINT = "https://www.knime.com/welcome-ap";

    private static JSONCategory[] TILE_CATEGORIES;

    private final boolean m_isFreshWorkspace;
    private final URL m_tileURL;
    private final ObjectMapper m_mapper;
    private final OfflineJsonCollector m_offlineCollector;


    /**
     * @param introPageFile the intro page file in the temporary directory
     * @param introFileLock lock for the intro file
     * @param isFreshWorkspace
     * @param customizationInfo
     */
    protected TileUpdater(final File introPageFile, final ReentrantLock introFileLock, final boolean isFreshWorkspace,
        final Map<String, String> customizationInfo) {
        super(introPageFile, introFileLock);
        m_isFreshWorkspace = isFreshWorkspace;
        m_mapper = new ObjectMapper();
        m_offlineCollector = new OfflineJsonCollector();
        m_tileURL = buildTileURL(customizationInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void prepareData() throws Exception {
        if (IntroPage.MOCK_INTRO_PAGE) {
            Thread.sleep(1500);
        }
        if (TILE_CATEGORIES == null) {
            try {
                HttpURLConnection conn = (HttpURLConnection)m_tileURL.openConnection();
                conn.setReadTimeout(5000);
                conn.setConnectTimeout(2000);
                conn.connect();

                final ClassLoader cl = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                try {
                    TILE_CATEGORIES = m_mapper.readValue(conn.getInputStream(), JSONCategory[].class);
                    Arrays.sort(TILE_CATEGORIES, (c1, c2) -> c1.getId().compareTo(c2.getId()));
                } finally {
                    Thread.currentThread().setContextClassLoader(cl);
                    conn.disconnect();
                }
            } catch (Exception e) {
                // offline or server not reachable
                LOGGER.info("Failed to load welcome page content, using offline tiles.");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void updateData() {
        String tiles;
        try {
            if (m_isFreshWorkspace) {
                hideElement("hub-search-bar");
                tiles = m_offlineCollector.fetchFirstUse();
            } else if (TILE_CATEGORIES != null) {
                tiles = buildTilesFromCategories();
            } else {
                tiles = m_offlineCollector.fetchAllOffline();
            }
            updateTiles(tiles);
        } catch (IOException e) {
            LOGGER.error("Could not display tiles: " + e.getMessage(), e);
        }
    }

    private String buildTilesFromCategories() throws JsonProcessingException {
        JSONTile[] tileArray = new JSONTile[3];
        for (int cat = 1; cat <= 3; cat++) {
            JSONTile chosenCategoryTile = null;
            for (JSONCategory jsonCategory : TILE_CATEGORIES) {
                if (jsonCategory.getId().startsWith("c" + cat)) {
                    List<JSONTile> tiles = jsonCategory.getTiles();
                    if (tiles.size() > 0) {
                        // naive approach, pick a random tile per a category, could use smarter method in the future
                        int chosenIndex = ThreadLocalRandom.current().nextInt(tiles.size());
                        chosenCategoryTile = tiles.get(chosenIndex);
                        // FIXME: temporary workaround for bug https://wunderkraut.atlassian.net/browse/KNIME-305
                        String buttonText = chosenCategoryTile.getLink();
                        chosenCategoryTile.setLink(chosenCategoryTile.getButtonText());
                        chosenCategoryTile.setButtonText(buttonText);
                    }
                }
            }
            if (chosenCategoryTile == null) {
                try {
                    chosenCategoryTile = m_offlineCollector.fetchSingleOfflineTile("C" + cat);
                } catch (IOException e) {
                    LOGGER.error("Could not retrieve offline tile: " + e.getMessage(), e);
                    chosenCategoryTile = new JSONTile();
                }
            }
            tileArray[cat - 1] = chosenCategoryTile;
        }
        return m_mapper.writeValueAsString(tileArray);
    }

    private static URL buildTileURL(final Map<String, String> customizationInfo) {
        StringBuilder builder = new StringBuilder(WELCOME_PAGE_ENDPOINT);
        builder.append("?knid=" + KNIMEConstants.getKNIMEInstanceID());
        builder.append("&version=" + KNIMEConstants.VERSION);
        // FIXME: needs to be omitted, until https://wunderkraut.atlassian.net/browse/KNIME-304 is fixed
        // builder.append("&os=" + Platform.getOS());
        // builder.append("&osname=" + KNIMEConstants.getOSVariant());
        builder.append("&arch=" + Platform.getOSArch());

        //If there is no custom URL set use the standard one
        if (customizationInfo.containsKey("companyName")) {
            //Add the customizers name to the URL
            String companyName = customizationInfo.get("companyName");
            if (StringUtils.isNoneEmpty(companyName)) {
                try {
                    builder.append("&brand=" + URLEncoder.encode(companyName, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    /* don't append customizer info */
                    LOGGER.warn("Could not add brand information to welcome page URL: " + e.getMessage(), e);
                }
            }
        }
        try {
            return new URL(builder.toString().replace(" ", "%20"));
        } catch (MalformedURLException e) {
            LOGGER.error("Could not construct welcome page URL: " + e.getMessage(), e);
            return null;
        }
    }

    private void hideElement(final String id) {
        executeUpdateInBrowser("hideElement('" + id + "');");
    }

    private void updateTiles(final String tiles) {
        executeUpdateInBrowser("updateTile(" + tiles + ");");
    }
}
