/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.org; Email: contact@knime.org
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
 */
package org.knime.product.rcp.addons;

import javax.inject.Inject;

import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.di.extensions.EventTopic;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.basic.MTrimBar;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.osgi.service.event.Event;

/**
 * @author Benjamin Moser, KNIME GmbH, Konstanz, Germany
 */
public class ToolbarFixAddon {

    @Inject
    private MApplication m_app;

    @Inject
    private EModelService m_modelService;

    /**
     * ID of the main eclipse top trim.
     */
    private static final String MAIN_TRIM_ID = "org.eclipse.ui.main.toolbar";

    /**
     * ID of the toolbar containing the button to switch to the Web UI perspective of the "KNIME UI" Extension.
     */
    private static final String SWITCH_TOOLBAR_ID = "org.knime.ui.java.toolbar.0";

    /**
     * Remove the "Open KNIME.next" button on shutdown s.t. it is not persisted with the application model. This is
     * useful because otherwise the button would still be available in disabled state after the extension has been
     * uninstalled. The button is added on each startup by the extension.
     *
     * @param event
     */
    @Inject
    @Optional
    public void removeSwitchToWebUIToolbar(@EventTopic(UIEvents.UILifeCycle.APP_SHUTDOWN_STARTED) final Event event) {
        var trim = m_modelService.findElements(m_app, MAIN_TRIM_ID, MTrimBar.class).get(0);
        var switchToolbar = m_modelService.find(SWITCH_TOOLBAR_ID, trim);
        if (switchToolbar != null) {
            trim.getChildren().remove(switchToolbar);
        }
    }

}
