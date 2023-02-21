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
 *   13 Jun 2020 (albrecht): created
 */
package org.knime.product.rcp;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.knime.workbench.explorer.view.actions.OpenKnimeUrlAction;

/**
 * {@link Listener} implementation to allow the opening of KNIME URLs (knime://).
 *
 * @author Christian Albrecht, KNIME GmbH, Konstanz, Germany
 */
public class KNIMEOpenUrlEventProcessor implements Listener {

    private ArrayList<String> m_urlsToOpen = new ArrayList<String>(1);

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleEvent(final Event event) {
        if (event.text != null) {
            m_urlsToOpen.add(event.text);
        }
    }

    /**
     * If there are URLs to open from an openURL-Event this method triggers the corresponding {@link OpenKnimeUrlAction}s
     */
    public void openUrls() {
        if (!OpenKnimeUrlAction.isEventHandlingActive() || m_urlsToOpen.isEmpty()) {
            // Nothing to do or Classic UI not active, clear events
            m_urlsToOpen.clear();
            return;
        }

        List<String> urlList = m_urlsToOpen.stream().filter((url) -> {
            return isKnimeUrl(url);
        }).collect(Collectors.toList());
        m_urlsToOpen.clear();

        IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        if (!urlList.isEmpty()) {
            OpenKnimeUrlAction a = new OpenKnimeUrlAction(activePage, urlList);
            a.run();
        }
    }

    static boolean isKnimeUrl(final String url) {
        String protocol = "knime://";
        int length = protocol.length();
        if (url != null && !url.isEmpty() && url.length() > length) {
            if (url.substring(0, length).equalsIgnoreCase(protocol)) {
                return true;
            }
        }
        return false;
    }

}
