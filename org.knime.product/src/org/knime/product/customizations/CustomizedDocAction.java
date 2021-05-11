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
 *   May 10, 2021 (hornm): created
 */
package org.knime.product.customizations;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.NodeLogger;

/**
 * Helper to access the customization info specific to the 'Documentation' help-menu entry.
 *
 * @author Moritz Heine, KNIME GmbH, Konstanz, Germany
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
public final class CustomizedDocAction {

    private static Optional<CustomizedDocAction> instance;

    private final String m_docsActionLabel;

    private final URL m_docsAddress;

    /**
     * @return the singleton instance or an empty optional if there is no customization info available
     */
    public static Optional<CustomizedDocAction> getInstance() {
        if (instance == null) {
            Map<String, String> customizationInfo = ICustomizationService.findServiceAndGetCustomizationInfo();
            URL docsAddress = getDocsAddress(customizationInfo);
            if (docsAddress != null) {
                instance = Optional.of(new CustomizedDocAction(getDocsActionLabel(customizationInfo), docsAddress));
            } else {
                instance = Optional.empty();
            }
        }
        return instance;
    }

    private CustomizedDocAction(final String docsActionLabel, final URL docsAddress) {
        if (docsActionLabel == null && docsAddress != null) {
            // default label if only the doc-address is given
            m_docsActionLabel = "Documentation";
        } else {
            m_docsActionLabel = docsActionLabel;
        }
        m_docsAddress = docsAddress;
    }

    /**
     * @return the label of the doc help-menu entry
     */
    public String getDocsActionLabel() {
        return m_docsActionLabel;
    }

    /**
     * @return the documentation-URL to be opened in the system browser
     */
    public URL getDocsAddress() {
        return m_docsAddress;
    }

    private static String getDocsActionLabel(final Map<String, String> customizationInfo) {
        String button = StringUtils.trim(customizationInfo.get("documentationButton"));
        if (button.isBlank() || button.equals("-")) {
            button = null;
        }
        return button;
    }

    private static URL getDocsAddress(final Map<String, String> customizationInfo) {
        if (customizationInfo.containsKey("documentationAddress")) {
            try {
                return new URL(customizationInfo.get("documentationAddress"));
            } catch (MalformedURLException e) {
                NodeLogger.getLogger(CustomizedDocAction.class)
                    .error("Could not parse provided link for property 'documentationAddress': "
                        + customizationInfo.get("documentationAddress"));
            }
        }
        return null;
    }

}
