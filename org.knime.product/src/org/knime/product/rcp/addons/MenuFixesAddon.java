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
 *   May 4, 2021 (hornm): created
 */
package org.knime.product.rcp.addons;

import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.di.extensions.EventTopic;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.menu.MMenu;
import org.eclipse.e4.ui.model.application.ui.menu.MMenuElement;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.knime.core.node.NodeLogger;
import org.osgi.service.event.Event;

import jakarta.inject.Inject;

/**
 * Add-on registered as fragment with the application model. It is called once the startup is complete and removes (or
 * at least hides) all unwanted menu entries. That is:
 * <ul>
 * <li>the 'Search' entry in the main menu</li>
 * <li>unwanted entries in the help-menu</li>
 * </ul>
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
public class MenuFixesAddon {

    private static final String ELEMENT_ID_HELP_MENU = "help";

    private static final String ELEMENT_ID_SEARCH_MENU = "org.eclipse.search.menu";

    private static final String ELEMENT_ID_IDE_WINDOW = "IDEWindow";

    private static final Set<String> MENU_ENTRY_IDS_TO_REMOVE = Set.of("helpContents", "helpSearch");

    /* Those entries refuse to be removed, but hiding them works */
    private static final Set<String> MENU_ENTRY_IDS_TO_HIDE = Set.of("org.eclipse.ui.actions.showKeyAssistHandler",
        "org.eclipse.ui.cheatsheets.actions.CheatSheetHelpMenuAction", "group.assist", "group.tutorials");

    @Inject
    private MApplication m_app;

    /**
     * Carries out the help-menu entry removal.
     *
     * @param event
     */
    @Inject
    @Optional
    public void removeMenuEntries(@EventTopic(UIEvents.UILifeCycle.APP_STARTUP_COMPLETE) final Event event) {
        fixSearchMenu(m_app);
        fixHelpMenu(m_app);
    }

    private static void fixSearchMenu(final MApplication app) {
        MMenuElement searchMenu = getMainMenuEntry(app, ELEMENT_ID_SEARCH_MENU);
        if (!(searchMenu instanceof MMenu)) {
            return;
        }
        searchMenu.setVisible(false);
        searchMenu.setToBeRendered(false);
    }

    private static void fixHelpMenu(final MApplication app) {
        MMenuElement helpMenu = getMainMenuEntry(app, ELEMENT_ID_HELP_MENU);
        if (!(helpMenu instanceof MMenu)) {
            NodeLogger.getLogger(MenuFixesAddon.class).error("No help menu found");
            return;
        }
        removeHelpMenuEntries((MMenu)helpMenu);
    }

    private static MMenuElement getMainMenuEntry(final MApplication app, final String id) {
        return app.getChildren().stream()//
            .filter(w -> ELEMENT_ID_IDE_WINDOW.equals(w.getElementId()))//
            .findFirst()//
            .map(w -> w.getMainMenu().getChildren().stream().filter(m -> id.equals(m.getElementId()))
                .findFirst().orElse(null))//
            .orElse(null);
    }

    private static void removeHelpMenuEntries(final MMenu helpMenu) {
        helpMenu.getChildren()
            .removeIf(e -> e.getElementId() != null && MENU_ENTRY_IDS_TO_REMOVE.contains(e.getElementId()));

        Set<MMenuElement> toHide = helpMenu.getChildren().stream()
            .filter(e -> e.getElementId() != null && MENU_ENTRY_IDS_TO_HIDE.contains(e.getElementId()))
            .collect(Collectors.toSet());
        toHide.forEach(e -> {
            e.setVisible(false);
            e.setToBeRendered(false);
        });
    }

}
