/*
 * ------------------------------------------------------------------------
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
 * ----------------------------------------------------------------------------
 */
package org.knime.product;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;

import org.eclipse.core.runtime.Platform;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.knime.core.node.NodeLogger;
import org.osgi.framework.BundleContext;

/**
 * The main plugin class to be used in the desktop.
 */
public class ProductPlugin extends AbstractUIPlugin {

    // The shared instance.
    private static ProductPlugin plugin;

    /** Plugin ID as defined in the plugin.xml file. */
    public static final String PLUGIN_ID = "org.knime.product";

    // Resource bundle.
    private ResourceBundle m_resourceBundle;

    /**
     * The constructor.
     */
    public ProductPlugin() {
        super();
        plugin = this;
    }

    /**
     * This method is called upon plug-in activation.
     *
     * @param context BundleContext
     * @throws Exception on error
     */
    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);
    }

    /**
     * This method is called when the plug-in is stopped.
     *
     * @param context BundleContext
     * @throws Exception on error
     *
     */
    @Override
    public void stop(final BundleContext context) throws Exception {
        super.stop(context);
        plugin = null;
        m_resourceBundle = null;
    }

    /**
     * @return Returns the shared instance.
     */
    public static ProductPlugin getDefault() {
        return plugin;
    }

    /**
     * @param key The resource key
     * @return the string from the plugin's resource bundle, or 'key' if not
     *         found.
     */
    public static String getResourceString(final String key) {
        ResourceBundle bundle = ProductPlugin.getDefault().getResourceBundle();
        try {
            return (bundle != null) ? bundle.getString(key) : key;
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /**
     * @return the plugin's resource bundle,
     */
    public ResourceBundle getResourceBundle() {
        try {
            if (m_resourceBundle == null) {
                m_resourceBundle = ResourceBundle
                        .getBundle("org.knime.product."
                                + "ProductPluginResources");
            }
        } catch (MissingResourceException x) {
            m_resourceBundle = null;
        }
        return m_resourceBundle;
    }

    /**
     * Determine and return the installation location of KNIME.
     *
     * @return A non-empty optional if the path can be determined, an empty optional in case of errors (logged to
     *         NodeLogger) or the installation path isn't local.
     */
    public static Optional<Path> getInstallationLocation() {
        Location loc = Platform.getInstallLocation();
        if (loc == null) {
            NodeLogger.getLogger(ProductPlugin.class).error("Cannot detect KNIME installation directory");
            return Optional.empty();
        } else if (!loc.getURL().getProtocol().equals("file")) {
            NodeLogger.getLogger(ProductPlugin.class).error("KNIME installation directory is not local");
            return Optional.empty();
        }

        String path = loc.getURL().getPath();
        if (Platform.OS_WIN32.equals(Platform.getOS()) && path.matches("^/[a-zA-Z]:/.*")) {
            // Windows path with drive letter => remove first slash
            path = path.substring(1);
        }
        try {
            return Optional.ofNullable(Paths.get(path)); // NOSONAR False positive. The path comes from `loc`.
        } catch (InvalidPathException e) {
            NodeLogger.getLogger(ProductPlugin.class).error("Unable to determine installation path from " + path, e);
            return Optional.empty();
        }
    }

}
