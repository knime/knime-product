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
package org.knime.product.rcp;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.jface.action.Action;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.DesktopUtil;

/**
 * Action that opens a URL either in the browser or e-mail-application.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
final class OpenURLAction extends Action {

    private URL m_url;

    private boolean m_isMailTo;

    OpenURLAction(final String id, final String label, final String tooltip, final String url, final boolean isMailTo) {
        this(id, label, tooltip, getUrl(url), isMailTo);
    }

    OpenURLAction(final String id, final String label, final String tooltip, final URL url, final boolean isMailTo) {
        super(label);
        m_url = url;
        m_isMailTo = isMailTo;
        setToolTipText(tooltip);
        setId(id);
    }

    private static URL getUrl(final String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            NodeLogger.getLogger(OpenURLAction.class).error("Invalid URL: " + url, e);
            return null;
        }
    }

    @Override
    public void run() {
        if (m_url != null) {
            if (m_isMailTo) {
                DesktopUtil.mailTo(m_url);
            } else {
                DesktopUtil.browse(m_url);
            }
        }
    }

}
