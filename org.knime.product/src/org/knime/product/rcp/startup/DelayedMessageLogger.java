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
 *   19 Jun 2020 (Marc Bux, KNIME GmbH, Berlin, Germany): created
 */
package org.knime.product.rcp.startup;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.knime.core.node.NodeLogger;

/**
 * A class that can queue log messages and log them at a later point in time. Its purpose here is that we want to delay
 * the loading of classes required for logging.
 *
 * @author Marc Bux, KNIME GmbH, Berlin, Germany
 */
final class DelayedMessageLogger {

    private enum LogMessageLevel {
            DEBUG, ERROR;
    }

    /**
     * Class that structures to-be-logged error messages together with their exceptions (for deferred logging).
     */
    private static final class LogMessage {

        private final LogMessageLevel m_level;

        private final String m_message;

        private final Exception m_exception;

        LogMessage(final LogMessageLevel level, final String message, final Exception exception) {
            m_level = level;
            m_message = message;
            m_exception = exception;
        }

        LogMessageLevel getLevel() {
            return m_level;
        }

        String getMessage() {
            return m_message;
        }

        Optional<Exception> getException() {
            return m_exception == null ? Optional.empty() : Optional.of(m_exception);
        }
    }

    private final List<LogMessage> m_log = new ArrayList<>();

    synchronized void queueDebug(final String message) {
        m_log.add(new LogMessage(LogMessageLevel.DEBUG, message, null));
    }

    synchronized void queueError(final String message) {
        m_log.add(new LogMessage(LogMessageLevel.ERROR, message, null));
    }

    synchronized void queueError(final String message, final Exception e) {
        m_log.add(new LogMessage(LogMessageLevel.ERROR, message, e));
    }

    synchronized void logQueuedMessaged(final NodeLogger logger) {
        for (final LogMessage logMessage : m_log) {
            final LogMessageLevel level = logMessage.getLevel();
            final String message = logMessage.getMessage();
            final Optional<Exception> exception = logMessage.getException();
            if (level == LogMessageLevel.DEBUG) {
                logger.debug(message);
            } else if (level == LogMessageLevel.ERROR) {
                if (exception.isPresent()) {
                    logger.error(message, exception.get());
                } else {
                    logger.error(message);
                }
            }
        }
    }
}
