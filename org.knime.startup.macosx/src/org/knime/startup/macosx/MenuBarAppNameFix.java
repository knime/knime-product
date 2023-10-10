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
 *   10 Oct 2023 (Manuel Hotz, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.startup.macosx;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.eclipse.swt.widgets.Display;
import org.knime.core.util.IEarlyStartup;

/**
 * Fix for macOS Sonoma 14.0
 * <a href="https://github.com/eclipse-platform/eclipse.platform.swt/issues/779">"NewApplication" Eclipse bug</a>.
 * Fixed in Eclipse 4.30 to be released in 2023-12. Can be removed when we upgrade to 2023-12.
 *
 * @author Manuel Hotz, KNIME GmbH, Konstanz, Germany
 */
public final class MenuBarAppNameFix implements IEarlyStartup {

    @Override
    public void run() {
        if (Boolean.getBoolean("java.awt.headless")) {
            return;
        }
        /*
         * Best-effort workaround for the swt "NewApplication" bug on macOS Sonoma until we can update
         * to Eclipse 4.30.
         *
         * Will still show "NewApplication" until this StartupHelper is executed.
         *
         * This block closely follows the code in cocoa Display, emulating the fix in eclipse-platform/#807
         * (https://github.com/eclipse-platform/eclipse.platform.swt/pull/807),
         * in which the submenu has to be supplied with a title.
         *
         * If there's a problem at any point, we just bail.
         */
        final var display = Display.getDefault();
        try {
            final var appField = display.getClass().getDeclaredField("application");
            if (appField == null) {
                return;
            }
            appField.setAccessible(true); // NOSONAR this field is not public
            final var application = appField.get(display);

            // Display.java: NSMenu mainmenu = application.mainMenu(); // NOSONAR for reference
            final var mainMenuMethod = getMethod(application, "mainMenu");
            final var mainMenu = mainMenuMethod.invoke(application);

            // Display.java: NSMenuItem appitem = mainmenu.itemAtIndex(0); // NOSONAR for reference
            final var itemAtIndexMethod = getMethod(mainMenu, "itemAtIndex", long.class);
            final var appItem = itemAtIndexMethod.invoke(mainMenu, 0);

            if (appItem != null) {
                // Display.java: NSMenu sm = appitem.submenu(); // NOSONAR for reference
                final var subMenuMethod = getMethod(appItem, "submenu");
                final var sm = subMenuMethod.invoke(appItem);

                // we cannot reference NSString.java directly, which we need as the argument to `setTitle`,
                // since it's in the fragment as well
                final var nsMenuClass = sm.getClass();
                final var setTitleMethod = Arrays.stream(nsMenuClass.getDeclaredMethods()) //
                        .filter(m -> m.getName().equals("setTitle")) //
                        .findAny().orElseThrow(NoSuchMethodException::new);
                final var types = setTitleMethod.getParameterTypes();
                if (types.length == 0) {
                    throw new IllegalArgumentException("No-args \"setTitle\" method");
                }
                final var nsStringClass = types[0];
                final var stringWithMethod = nsStringClass.getDeclaredMethod("stringWith", String.class);

                // Display.java: sm.setTitle(name); // NOSONAR for reference
                final var nsStringInstance = stringWithMethod.invoke(null, Display.getAppName());
                setTitleMethod.invoke(sm, nsStringInstance);

            }
        } catch (final Exception e) { // NOSONAR we want to catch anything and bail
            // don't use NodeLogger in IEarlyStartup!
        }
    }

    private static Method getMethod(final Object obj, final String name, final Class<?>... parameters)
            throws NoSuchMethodException {
        return obj.getClass().getDeclaredMethod(name, parameters);
    }


}
