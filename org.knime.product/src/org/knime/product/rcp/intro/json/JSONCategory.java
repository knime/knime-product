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
 *   17 Sep 2019 (albrecht): created
 */
package org.knime.product.rcp.intro.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A POJO representing a category (one column) of tiles from the welcome page. A category can contain one or more tiles
 * but only one tile is displayed.
 *
 * @author Christian Albrecht, KNIME GmbH, Konstanz, Germany
 * @since 4.1
 */
@JsonAutoDetect
@JsonInclude(Include.NON_NULL)
public class JSONCategory {

    private String m_id;
    private String m_title;
    private String m_text;
    private List<JSONTile> m_tiles;

    /**
     * @return the id
     */
    @JsonProperty("category-id")
    public String getId() {
        return m_id;
    }

    /**
     * @param id the id to set
     */
    @JsonProperty("category-id")
    public void setId(final String id) {
        m_id = id;
    }

    /**
     * @return the title
     */
    @JsonProperty("category-title")
    public String getTitle() {
        return m_title;
    }

    /**
     * @param title the title to set
     */
    @JsonProperty("category-title")
    public void setTitle(final String title) {
        m_title = title;
    }

    /**
     * @return the text
     */
    @JsonProperty("category-text")
    public String getText() {
        return m_text;
    }

    /**
     * @param text the text to set
     */
    @JsonProperty("category-text")
    public void setText(final String text) {
        m_text = text;
    }

    /**
     * @return the tiles
     */
    @JsonProperty("tiles")
    public List<JSONTile> getTiles() {
        return m_tiles;
    }

    /**
     * @param tiles the tiles to set
     */
    @JsonProperty("tiles")
    public void setTiles(final List<JSONTile> tiles) {
        m_tiles = tiles;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @JsonIgnore
    public String toString() {
        return m_title;
    }

}
