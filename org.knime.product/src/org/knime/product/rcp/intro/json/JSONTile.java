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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Java POJO which holds the information about one tile of the welcome page and can be serialized to/from JSON
 *
 * @author Christian Albrecht, KNIME GmbH, Konstanz, Germany
 * @since 4.1
 */
@JsonAutoDetect
@JsonInclude(Include.NON_NULL)
public class JSONTile {

    private String m_id;
    private String m_title;
    private String m_tag;
    private String m_image;
    private String m_icon;
    private String m_text;
    private String m_buttonText;
    private String m_link;
    private String m_welcomeButtonClass;

    /**
     * @return the id
     */
    @JsonProperty("tile-category-id")
    public String getId() {
        return m_id;
    }

    /**
     * @param id the id to set
     */
    @JsonProperty("tile-category-id")
    public void setId(final String id) {
        m_id = id;
    }

    /**
     * @return the title
     */
    @JsonProperty("tile-title")
    public String getTitle() {
        return m_title;
    }

    /**
     * @param title the title to set
     */
    @JsonProperty("tile-title")
    public void setTitle(final String title) {
        m_title = title;
    }

    /**
     * @return the tag
     */
    @JsonProperty("tile-tag")
    public String getTag() {
        return m_tag;
    }

    /**
     * @param tag the tag to set
     */
    @JsonProperty("tile-tag")
    public void setTag(final String tag) {
        m_tag = tag;
    }

    /**
     * @return the image
     */
    @JsonProperty("tile-image")
    public String getImage() {
        return m_image;
    }

    /**
     * @param image the image to set
     */
    @JsonProperty("tile-image")
    public void setImage(final String image) {
        m_image = image;
    }

    /**
     * @return the icon
     */
    @JsonProperty("tile-icon")
    public String getIcon() {
        return m_icon;
    }

    /**
     * @param icon the icon to set
     */
    @JsonProperty("tile-icon")
    public void setIcon(final String icon) {
        m_icon = icon;
    }

    /**
     * @return the text
     */
    @JsonProperty("tile-text")
    public String getText() {
        return m_text;
    }

    /**
     * @param text the text to set
     */
    @JsonProperty("tile-text")
    public void setText(final String text) {
        m_text = text;
    }

    /**
     * @return the buttonText
     */
    @JsonProperty("tile-button-text")
    public String getButtonText() {
        return m_buttonText;
    }

    /**
     * @param buttonText the buttonText to set
     */
    @JsonProperty("tile-button-text")
    public void setButtonText(final String buttonText) {
        m_buttonText = buttonText;
    }

    /**
     * @return the link
     */
    @JsonProperty("tile-link")
    public String getLink() {
        return m_link;
    }

    /**
     * @param link the link to set
     */
    @JsonProperty("tile-link")
    public void setLink(final String link) {
        m_link = link;
    }

    /**
     * @return the buttonClass of the welcome tile-button
     */
    @JsonProperty("tile-welcome-button-class")
    public String getWelcomeButtonClass() {
        return m_welcomeButtonClass;
    }

    /**
     * @param buttonClass the styles to set for the welcome tile-button
     */
    @JsonProperty("tile-welcome-button-class")
    public void setWelcomeButtonClass(final String buttonClass) {
        m_welcomeButtonClass = buttonClass;
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
