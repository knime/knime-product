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
 */
package org.knime.product.rcp.addons;

import java.util.List;

import javax.inject.Inject;

import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.di.extensions.EventTopic;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.advanced.MPerspectiveStack;
import org.eclipse.e4.ui.model.application.ui.basic.MPartSashContainer;
import org.eclipse.e4.ui.model.application.ui.basic.MTrimmedWindow;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.osgi.service.event.Event;

/**
 * Add-on registered as fragment with the application model. Fixes a problem with the state of the perspective, i.e.
 * there is sometimes an empty superfluous perspective. The reason why it's there is still not known. This add-on fixes
 * the application model by simply removing that empty, superfluous perspective before shutdown such that it's not saved
 * and, thus, remains absent on the next start.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
public final class PerspectiveFixAddon {

    @Inject
    private MApplication m_app;

    @Inject
    private EModelService m_modelService;

    /**
     * The ID of the "main" perspective stack. This is where perspectives used by the workbench commonly live.
     */
    private static final String MAIN_PERSPECTIVE_STACK_ID = "org.eclipse.ui.ide.perspectivestack";

    /**
     * The ID  of the Web UI perspective of the "KNIME UI" Extension.
     */
    private static final String WEBUI_PERSPECTIVE_ID = "org.knime.ui.java.perspective";

    /**
     * Hack to remove an empty, superfluous perspective (which is sometimes present for a still unknown reasons) on
     * shutdown such that it's not saved.
     *
     * @param event
     */
    @Inject
    @Optional
    @SuppressWarnings({"java:S3776", "java:S134"})
    public void
        removeEmptyPerspectiveOnShutdown(@EventTopic(UIEvents.UILifeCycle.APP_SHUTDOWN_STARTED) final Event event) {
        List<MPerspectiveStack> perspectiveStacks = m_modelService.findElements(m_app, null, MPerspectiveStack.class);
        if (perspectiveStacks != null && perspectiveStacks.size() == 2) {
            for (MPerspectiveStack stack : perspectiveStacks) {
                if (stack.getChildren().isEmpty()) {
                    // if there is an empty second perspective stack
                    // -> remove it by removing its parent (PartSashContainer) from the parent (TrimmedWindow)
                    Object parent = stack.getParent();
                    if (parent instanceof MPartSashContainer) {
                        Object parentParent = ((MPartSashContainer)parent).getParent();
                        if (parentParent instanceof MTrimmedWindow) {
                            ((MTrimmedWindow)parentParent).getChildren().remove(parent);
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * Remove the "Web UI" (AP.NEXT) perspective, s.t. it is not persisted with the application model. This is useful
     * because otherwise the perspective would still be an available choice even after the "KNIME UI" Extension has been
     * uninstalled. The perspective is added on each startup by the extension.
     *
     * @param event
     */
    @Inject
    @Optional
    public void removeWebUIPerspective(@EventTopic(UIEvents.UILifeCycle.APP_SHUTDOWN_STARTED) final Event event) {
        var mainPerspectiveStack =
            m_modelService.findElements(m_app, MAIN_PERSPECTIVE_STACK_ID, MPerspectiveStack.class).get(0);
        var webUIPerspective = m_modelService.find(WEBUI_PERSPECTIVE_ID, mainPerspectiveStack);
        if (webUIPerspective != null) {
            mainPerspectiveStack.getChildren().remove(webUIPerspective);
        }
    }
}
