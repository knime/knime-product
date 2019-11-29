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
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.Platform;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.HubStatistics;
import org.knime.product.rcp.intro.json.JSONCategory;
import org.knime.product.rcp.intro.json.JSONTile;
import org.knime.product.rcp.intro.json.OfflineJsonCollector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 *
 * @author Christian Albrecht, KNIME GmbH, Konstanz, Germany
 * @since 4.1
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
        if (m_tileURL == null) {
            return;
        }
        if (TILE_CATEGORIES == null || IntroPage.MOCK_INTRO_PAGE) {
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
            if (TILE_CATEGORIES == null) {
                if (m_isFreshWorkspace) {
                    tiles = m_offlineCollector.fetchFirstUse();
                } else {
                    tiles = m_offlineCollector.fetchAllOffline();
                }
            } else {
                tiles = buildTilesFromCategories();
            }
            if (m_isFreshWorkspace) {
                hideElement("hub-search-bar");
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
                        // naive approach, pick first tile per category, could use smarter method in the future
                        // int chosenIndex = ThreadLocalRandom.current().nextInt(tiles.size());
                        chosenCategoryTile = tiles.get(0);
                    }
                }
            }
            if (chosenCategoryTile == null || m_isFreshWorkspace) {
                try {
                    chosenCategoryTile = m_offlineCollector.fetchSingleOfflineTile("C" + cat, m_isFreshWorkspace);
                } catch (IOException e) {
                    LOGGER.error("Could not retrieve offline tile: " + e.getMessage(), e);
                    chosenCategoryTile = new JSONTile();
                }
            }
            tileArray[cat - 1] = chosenCategoryTile;
        }
        return m_mapper.writeValueAsString(tileArray);
    }

    private URL buildTileURL(final Map<String, String> customizationInfo) {
        StringBuilder builder = new StringBuilder(WELCOME_PAGE_ENDPOINT);
        builder.append("?knid=" + KNIMEConstants.getKNIMEInstanceID());
        builder.append("&version=" + KNIMEConstants.VERSION);
        builder.append("&os=" + Platform.getOS());
        builder.append("&osname=" + KNIMEConstants.getOSVariant());
        builder.append("&arch=" + Platform.getOSArch());

        // customization if present
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

        // details
        builder.append("&details=");
        builder.append(buildAPUsage());
        builder.append(",");
        builder.append(buildHubUsage());

        try {
            return new URL(builder.toString().replace(" ", "%20"));
        } catch (MalformedURLException e) {
            LOGGER.error("Could not construct welcome page URL: " + e.getMessage(), e);
            return null;
        }
    }

    private static String buildHubUsage() {
        String hubUsage = "hubUsage:";
        Optional<ZonedDateTime> lastLogin = Optional.empty();
        Optional<ZonedDateTime> lastUpload = Optional.empty();
        try {
            lastLogin = HubStatistics.getLastLogin();
            lastUpload = HubStatistics.getLastUpload();
        } catch (Exception e) {
            LOGGER.info("Hub statistics could not be fetched: " + e.getMessage(), e);
        }

        if (lastUpload.isPresent()) {
            hubUsage += "contributer";
        } else if (lastLogin.isPresent()) {
            hubUsage += "user";
        } else {
            hubUsage += "none";
        }
        return hubUsage;
    }

    private String buildAPUsage() {
        // simple distinction between first and recurring users
        String apUsage = "apUsage:";
        if (m_isFreshWorkspace) {
            apUsage += "first";
        } else {
            apUsage += "recurring";
        }
        return apUsage;
    }


    private void hideElement(final String id) {
        executeUpdateInBrowser("hideElement('" + id + "');");
    }

    private void updateTiles(final String tiles) {
        executeUpdateInBrowser("updateTile(" + tiles + ");");
    }
}
