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
 *   Apr 3, 2025 (lw): created
 */
package org.knime.product.profiles;

import java.util.Set;

import org.apache.commons.lang3.function.FailableConsumer;
import org.eclipse.core.internal.preferences.DefaultPreferences;
import org.eclipse.core.internal.preferences.PreferencesService;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.knime.core.node.util.ClassUtils;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

/**
 * A helper for clearing {@link Preferences} nodes for tests.
 * Enables independent tests below (no cached values between tests).
 *
 * @author Leon Wenzler, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
final class TestPreferencesContext {

    /**
     * Provides an {@link IEclipsePreferences} object for the given anonymous test,
     * and then clears the requested node ID from Eclipse's caches.
     */
    static void getDefaultPreferences(final String id,
        final FailableConsumer<IEclipsePreferences, Exception> test) throws Exception {
        try {
            // Clearing preferences from previous, non-profile plugin tests, which may have
            // modified some Eclipse preferences or loaded nodes.
            clearDefaultPreferences(id);
            test.accept(DefaultScope.INSTANCE.getNode(id));
        } finally {
            // Clearing preferences for next, non-profile plugin tests, which may rely
            // on the true default values upon initialization, not ours from testing.
            clearDefaultPreferences(id);
        }
    }

    /**
     * Removes the given IDs from the node cache.
     */
    static void clearDefaultPreferences(final String... ids)
        throws BackingStoreException, ReflectiveOperationException {
        for (var id : ids) {
            DefaultScope.INSTANCE.getNode(id).removeNode();

            // Unfortunately, `IEclipsePreferences#removeNode` does not remove the node
            // from its set of loaded nodes, which determines whether a queried node (after it is
            // recreated - that is ensured above) is also initialized.
            final var preferences = PreferencesService.getDefault().getRootNode().node(DefaultScope.INSTANCE.getName());
            if (preferences instanceof DefaultPreferences) {
                final var field = preferences.getClass().getDeclaredField("LOADED_NODES");
                field.setAccessible(true); // NOSONAR
                ClassUtils.castOptional(Set.class, field.get(preferences)).ifPresent(s -> s.remove(id));
            }
        }
    }
}
