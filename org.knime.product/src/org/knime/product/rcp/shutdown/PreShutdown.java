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
 *   Jan 31, 2023 (hornm): created
 */
package org.knime.product.rcp.shutdown;

import java.util.Iterator;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.knime.core.node.NodeLogger;

/**
 * Callback to get informed right before shutdown.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
public interface PreShutdown {

    /**
     * Called immediately before the workbench is being shut down; i.e. right before any window is being closed.
     *
     * @return <code>true</code> to allow the workbench to proceed with shutdown, <code>false</code> to veto a
     *         non-forced shutdown
     */
    boolean onPreShutdown();

    /**
     * Helper to execute the code registered at this extension point. Must be called at the 'pre-shutdown' of the KNIME
     * Application.
     *
     * @return <code>true</code> to allow the workbench to proceed with shutdown, <code>false</code> to veto a
     *         non-forced shutdown
     *
     * @noreference This method is not intended to be referenced by clients.
     */
    public static boolean preShutdown() {
        var extPointId = "org.knime.product.preShutdown";
        IExtensionRegistry registry = Platform.getExtensionRegistry();
        IExtensionPoint point = registry.getExtensionPoint(extPointId);
        assert point != null : "Invalid extension point id: " + extPointId;

        Iterator<IConfigurationElement> it =
            Stream.of(point.getExtensions()).flatMap(ext -> Stream.of(ext.getConfigurationElements())).iterator();
        while (it.hasNext()) {
            IConfigurationElement e = it.next();
            try {
                if (!((PreShutdown)e.createExecutableExtension("class")).onPreShutdown()) {
                    return false;
                }
            } catch (CoreException ex) {
                NodeLogger.getLogger(PreShutdown.class)
                    .error("Could not create pre-shutdown object of class '" + e.getAttribute("class") + "' "
                        + "from plug-in '" + e.getContributor().getName() + "': " + ex.getMessage(), ex);
            } catch (Exception ex) {
                NodeLogger.getLogger(PreShutdown.class)
                    .error("Pre-shutdown in '" + e.getAttribute("class") + " from plug-in '"
                        + e.getContributor().getName() + "' has thrown an uncaught exception: " + ex.getMessage(), ex);
            }
        }
        return true;
    }

}
