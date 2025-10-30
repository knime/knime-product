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

/**
 * Interface for warmstart actions that can be executed during the KNIME warmstart application phase.
 * 
 * <p>
 * Warmstart actions are designed to perform initialization tasks that are typically expensive during
 * regular application startup, such as pre-installing conda environments, initializing caches, or
 * preparing other resources. By executing these during a separate warmstart phase (e.g., in Docker
 * container preparation), subsequent application starts can be significantly faster.
 * </p>
 * 
 * <p>
 * Implementations should be registered via the {@code org.knime.product.warmstartAction} extension point.
 * The warmstart application will discover and execute all registered actions during its initialization phase.
 * </p>
 * 
 * <p>
 * Actions should be designed to:
 * <ul>
 * <li>Be idempotent - safe to run multiple times</li>
 * <li>Handle failures gracefully without breaking the entire warmstart process</li>
 * <li>Provide meaningful progress reporting through logging</li>
 * <li>Avoid UI dependencies (warmstart runs in headless mode)</li>
 * </ul>
 * </p>
 * 
 * @author Marc Lehner, KNIME AG, Zurich, Switzerland
 * @since 5.9
 * @noreference This interface is not intended to be referenced by clients outside of KNIME.
 * @noextend This interface is not intended to be extended by clients outside of KNIME.
 */
public interface IWarmstartAction {

    /**
     * Executes the warmstart action.
     * 
     * <p>
     * This method will be called during the warmstart application phase. Implementations should
     * perform their initialization tasks and return a result indicating success or failure.
     * </p>
     * 
     * <p>
     * The method should not throw exceptions for recoverable errors. Instead, it should log
     * appropriate error messages and return {@link WarmstartResult#failure(String, Throwable)}.
     * Only throw exceptions for truly unrecoverable situations that should abort the entire
     * warmstart process.
     * </p>
     * 
     * @return a {@link WarmstartResult} indicating the outcome of the action
     * @throws Exception if an unrecoverable error occurs that should abort the warmstart process
     */
    WarmstartResult execute() throws Exception;

    /**
     * Returns a human-readable name for this warmstart action.
     * 
     * <p>
     * This name will be used in logging and diagnostic output to identify the action.
     * It should be descriptive but concise, e.g., "Conda Environment Installation",
     * "Extension Cache Initialization", etc.
     * </p>
     * 
     * @return the name of this warmstart action, never {@code null}
     */
    String getName();

    /**
     * Returns the priority of this warmstart action.
     * 
     * <p>
     * Actions with higher priority values are executed first. This allows controlling
     * the execution order when dependencies exist between actions. If multiple actions
     * have the same priority, their execution order is undefined.
     * </p>
     * 
     * <p>
     * Common priority ranges:
     * <ul>
     * <li>1000+ : Critical infrastructure (e.g., core plugin initialization)</li>
     * <li>500-999 : Important preparatory tasks (e.g., environment setup)</li>
     * <li>100-499 : Standard initialization tasks</li>
     * <li>1-99 : Low priority tasks</li>
     * </ul>
     * </p>
     * 
     * @return the priority of this action, higher values execute first
     */
    default int getPriority() {
        return 100; // Default priority for most actions
    }

    /**
     * Indicates whether this action should be executed even if previous actions have failed.
     * 
     * <p>
     * By default, if any warmstart action fails, subsequent actions are skipped to prevent
     * cascading failures. However, some actions (like cleanup tasks) might need to run
     * regardless of previous failures.
     * </p>
     * 
     * @return {@code true} if this action should execute even after failures, {@code false} otherwise
     */
    default boolean executeAfterFailures() {
        return false;
    }

    /**
     * Result of a warmstart action execution.
     * 
     * @param isSuccessful whether the action completed successfully
     * @param message optional message describing the result (for logging)
     * @param throwable optional throwable associated with the result (typically for failures)
     */
    record WarmstartResult(boolean isSuccessful, String message, Throwable throwable) {
        
        /**
         * Creates a successful result.
         * 
         * @return a successful warmstart result
         */
        public static WarmstartResult success() {
            return new WarmstartResult(true, null, null);
        }
        
        /**
         * Creates a successful result with a message.
         * 
         * @param message descriptive message about the success
         * @return a successful warmstart result with message
         */
        public static WarmstartResult success(String message) {
            return new WarmstartResult(true, message, null);
        }
        
        /**
         * Creates a failure result with a message.
         * 
         * @param message descriptive message about the failure
         * @return a failed warmstart result
         */
        public static WarmstartResult failure(String message) {
            return new WarmstartResult(false, message, null);
        }
        
        /**
         * Creates a failure result with a message and throwable.
         * 
         * @param message descriptive message about the failure
         * @param throwable the throwable that caused the failure
         * @return a failed warmstart result
         */
        public static WarmstartResult failure(String message, Throwable throwable) {
            return new WarmstartResult(false, message, throwable);
        }
    }
}