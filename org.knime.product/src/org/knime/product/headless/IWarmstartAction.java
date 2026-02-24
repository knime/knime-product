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
 *   Nov 07, 2025 (marc lehner): created
 */
package org.knime.product.headless;

/**
 * Interface for warmstart actions that can be executed during the KNIME warmstart application phase.
 *
 * <p>
 * Warmstart actions are designed to perform initialization tasks that are typically expensive during regular
 * application startup, such as pre-installing conda environments, initializing caches, or preparing other resources. By
 * executing these during a separate warmstart phase (e.g., in Docker container preparation), subsequent application
 * starts can be significantly faster.
 * </p>
 *
 * <p>
 * Implementations should be registered via the {@code org.knime.product.warmstartAction} extension point. All action
 * metadata (name, priority, executeAfterFailures) should be specified in the extension point declaration rather than in
 * the implementation class.
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
 * @since 5.8.2
 * @noreference This interface is not intended to be referenced by clients outside of KNIME.
 * @noextend This interface is not intended to be extended by clients outside of KNIME.
 */
public interface IWarmstartAction {

    /**
     * Executes the warmstart action.
     *
     * <p>
     * This method will be called during the warmstart application phase. Implementations should perform their
     * initialization tasks and return a result indicating success or failure.
     * </p>
     *
     * <p>
     * Action metadata such as name, priority, and executeAfterFailures behavior should be specified in the extension
     * point declaration (plugin.xml), not in the implementation class.
     * </p>
     *
     * @return a {@link WarmstartResult} indicating the outcome of the action
     * @throws Exception if an unrecoverable error occurs that should abort the warmstart process
     */
    WarmstartResult execute() throws Exception;

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
        public static WarmstartResult success(final String message) {
            return new WarmstartResult(true, message, null);
        }

        /**
         * Creates a failure result with a message.
         *
         * @param message descriptive message about the failure
         * @return a failed warmstart result
         */
        public static WarmstartResult failure(final String message) {
            return new WarmstartResult(false, message, null);
        }

        /**
         * Creates a failure result with a message and throwable.
         *
         * @param message descriptive message about the failure
         * @param throwable the throwable that caused the failure
         * @return a failed warmstart result
         */
        public static WarmstartResult failure(final String message, final Throwable throwable) {
            return new WarmstartResult(false, message, throwable);
        }
    }
}