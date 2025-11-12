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
 *   Nov 11, 2025 (wiswedel): created
 */
package org.knime.product.headless;

/**
 * Interface for batch executors used in headless KNIME. This interface is not meant to be implemented by third-party
 * developers, instead it is contributed via a service implementation that lives in the {@code knime-ap-batch}
 * repository.
 *
 * @author Bernd Wiswedel
 * @noimplement This interface is not intended to be implemented by clients.
 * @since 5.9
 */
public interface IBatchExecutor {

    /**
     * Return code for successful execution: {@value} .
     */
    int EXIT_SUCCESS = 0;

    /**
     * Return code for execution with warnings: {@value} .
     */
    int EXIT_WARN = 1;

    /**
     * Return code for errors before the workflow has been loaded (e.g. wrong parameter): {@value} .
     */
    int EXIT_ERR_PRESTART = 2;

    /**
     * Return code for errors during workflow loading: {@value} .
     */
    int EXIT_ERR_LOAD = 3;

    /**
     * Return code for errors during workflow execution: {@value} .
     */
    int EXIT_ERR_EXECUTION = 4;

    /**
     * Return code for errors due to missing batch executor extension: {@value} .
     */
    int EXIT_ERR_NOT_INSTALLED = 5;

    /**
     * The actual run method of the batch executor.
     * @param args command line arguments
     * @return exit code as defined in this interface
     */
    int run(String[] args);

}