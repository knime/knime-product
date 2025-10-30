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
package org.knime.product.headless;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.knime.core.node.NodeLogger;
import org.knime.product.headless.IWarmstartAction.WarmstartResult;

/**
 * Registry for discovering and executing warmstart actions contributed via the
 * {@code org.knime.product.warmstartAction} extension point.
 * 
 * <p>
 * This registry handles the discovery, sorting by priority, and execution of all registered
 * warmstart actions. It provides comprehensive logging and error handling to ensure that
 * failures in individual actions don't prevent other actions from executing.
 * </p>
 * 
 * @author Marc Lehner, KNIME AG, Zurich, Switzerland
 * @since 5.9
 */
public final class WarmstartActionRegistry {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(WarmstartActionRegistry.class);
    
    private static final String EXTENSION_POINT_ID = "org.knime.product.warmstartAction";
    
    private WarmstartActionRegistry() {
        // Utility class - no instantiation
    }
    
    /**
     * Discovers and executes all registered warmstart actions.
     * 
     * <p>
     * Actions are executed in priority order (highest priority first). If an action fails
     * and {@code executeAfterFailures} is false, subsequent actions are skipped unless
     * they have {@code executeAfterFailures} set to true.
     * </p>
     * 
     * @return a summary of the execution results
     */
    public static WarmstartExecutionSummary executeAllActions() {
        LOGGER.info("=== STARTING WARMSTART ACTION EXECUTION ===");
        
        List<WarmstartActionDescriptor> descriptors = discoverWarmstartActions();
        
        if (descriptors.isEmpty()) {
            LOGGER.info("No warmstart actions found");
            return new WarmstartExecutionSummary(0, 0, 0, 0);
        }
        
        // Sort by priority (highest first)
        descriptors.sort(Comparator.comparingInt(WarmstartActionDescriptor::priority).reversed());
        
        LOGGER.info("Found " + descriptors.size() + " warmstart action(s)");
        
        int executed = 0;
        int successful = 0;
        int failed = 0;
        int skipped = 0;
        boolean hasFailure = false;
        
        for (WarmstartActionDescriptor descriptor : descriptors) {
            LOGGER.info("--- Executing warmstart action: " + descriptor.name() + " (priority: " + descriptor.priority() + ") ---");
            
            // Check if we should skip due to previous failures
            if (hasFailure && !descriptor.executeAfterFailures()) {
                LOGGER.info("Skipping '" + descriptor.name() + "' due to previous failures");
                skipped++;
                continue;
            }
            
            executed++;
            
            try {
                IWarmstartAction action = createActionInstance(descriptor);
                WarmstartResult result = action.execute();
                
                if (result.isSuccessful()) {
                    successful++;
                    String message = result.message() != null ? result.message() : "completed successfully";
                    LOGGER.info("✓ '" + descriptor.name() + "' " + message);
                } else {
                    failed++;
                    hasFailure = true;
                    String message = result.message() != null ? result.message() : "failed";
                    if (result.throwable() != null) {
                        LOGGER.error("✗ '" + descriptor.name() + "' " + message, result.throwable());
                    } else {
                        LOGGER.error("✗ '" + descriptor.name() + "' " + message);
                    }
                }
                
            } catch (Exception e) {
                failed++;
                hasFailure = true;
                LOGGER.error("✗ '" + descriptor.name() + "' failed with exception", e);
            }
        }
        
        WarmstartExecutionSummary summary = new WarmstartExecutionSummary(
                descriptors.size(), executed, successful, failed);
        
        LOGGER.info("=== WARMSTART ACTION EXECUTION COMPLETE ===");
        LOGGER.info("Total actions: " + summary.totalActions() + ", Executed: " + summary.executedActions() + 
                ", Successful: " + summary.successfulActions() + ", Failed: " + summary.failedActions() + 
                ", Skipped: " + skipped);
        
        return summary;
    }
    
    /**
     * Discovers all warmstart actions registered via the extension point.
     * 
     * @return list of warmstart action descriptors, never null
     */
    private static List<WarmstartActionDescriptor> discoverWarmstartActions() {
        List<WarmstartActionDescriptor> descriptors = new ArrayList<>();
        
        IExtensionRegistry registry = Platform.getExtensionRegistry();
        IExtensionPoint extensionPoint = registry.getExtensionPoint(EXTENSION_POINT_ID);
        
        if (extensionPoint == null) {
            LOGGER.debug("Extension point '" + EXTENSION_POINT_ID + "' not found");
            return descriptors;
        }
        
        for (IConfigurationElement element : extensionPoint.getConfigurationElements()) {
            if ("warmstartAction".equals(element.getName())) {
                try {
                    WarmstartActionDescriptor descriptor = parseActionDescriptor(element);
                    descriptors.add(descriptor);
                    LOGGER.debug("Discovered warmstart action: " + descriptor.name() + " (class: " + 
                            descriptor.className() + ", priority: " + descriptor.priority() + ")");
                } catch (Exception e) {
                    LOGGER.error("Failed to parse warmstart action from extension: " + 
                            element.getContributor().getName(), e);
                }
            }
        }
        
        return descriptors;
    }
    
    /**
     * Parses a warmstart action descriptor from a configuration element.
     * 
     * @param element the configuration element
     * @return the parsed descriptor
     */
    private static WarmstartActionDescriptor parseActionDescriptor(IConfigurationElement element) {
        String id = element.getAttribute("id");
        String name = element.getAttribute("name");
        String className = element.getAttribute("class");
        String description = element.getAttribute("description");
        
        // Parse priority (default to 100)
        int priority = 100;
        String priorityStr = element.getAttribute("priority");
        if (priorityStr != null && !priorityStr.trim().isEmpty()) {
            try {
                priority = Integer.parseInt(priorityStr.trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid priority value '" + priorityStr + "' for warmstart action '" + name + 
                        "', using default: " + priority);
            }
        }
        
        // Parse executeAfterFailures (default to false)
        boolean executeAfterFailures = false;
        String executeAfterFailuresStr = element.getAttribute("executeAfterFailures");
        if (executeAfterFailuresStr != null && !executeAfterFailuresStr.trim().isEmpty()) {
            executeAfterFailures = Boolean.parseBoolean(executeAfterFailuresStr.trim());
        }
        
        return new WarmstartActionDescriptor(id, name, className, description, priority, 
                executeAfterFailures, element);
    }
    
    /**
     * Creates an instance of a warmstart action from its descriptor.
     * 
     * @param descriptor the action descriptor
     * @return the created action instance
     * @throws CoreException if the action cannot be created
     */
    private static IWarmstartAction createActionInstance(WarmstartActionDescriptor descriptor) 
            throws CoreException {
        Object actionObj = descriptor.configElement().createExecutableExtension("class");
        
        if (!(actionObj instanceof IWarmstartAction)) {
            throw new CoreException(org.eclipse.core.runtime.Status.error(
                    "Class '" + descriptor.className() + "' does not implement IWarmstartAction"));
        }
        
        return (IWarmstartAction) actionObj;
    }
    
    /**
     * Descriptor for a warmstart action discovered from the extension point.
     * 
     * @param id unique identifier
     * @param name human-readable name
     * @param className fully qualified class name
     * @param description optional description
     * @param priority execution priority
     * @param executeAfterFailures whether to execute after failures
     * @param configElement the configuration element (for creating instances)
     */
    private record WarmstartActionDescriptor(
            String id,
            String name, 
            String className,
            String description,
            int priority,
            boolean executeAfterFailures,
            IConfigurationElement configElement) {
    }
    
    /**
     * Summary of warmstart action execution results.
     * 
     * @param totalActions total number of actions discovered
     * @param executedActions number of actions that were executed
     * @param successfulActions number of actions that completed successfully
     * @param failedActions number of actions that failed
     */
    public record WarmstartExecutionSummary(
            int totalActions,
            int executedActions, 
            int successfulActions,
            int failedActions) {
        
        /**
         * @return number of actions that were skipped due to failures
         */
        public int skippedActions() {
            return totalActions - executedActions;
        }
        
        /**
         * @return true if all executed actions were successful
         */
        public boolean allSuccessful() {
            return executedActions > 0 && failedActions == 0;
        }
        
        /**
         * @return true if any actions failed
         */
        public boolean hasFailures() {
            return failedActions > 0;
        }
    }
}