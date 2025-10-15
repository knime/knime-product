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

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.knime.core.node.NodeLogger;

/**
 * A singleton class that detects overly long startups on Windows and, in such cases, displays a information dialog and
 * logs a warning. Otherwise (even when not on Windows), it logs the startup time as debug message.
 *
 * @author Marc Bux, KNIME GmbH, Berlin, Germany
 */
public final class LongStartupHandler {

    private static final class LongStartupDetectedDialog extends MessageDialogWithToggleAndURL {

        private static final String TITLE = "Startup of KNIME Analytics Platform is taking long";

        private static final String SUMMARY = String.format("Startup of KNIME Analytics Platform is taking "
            + "longer than %d seconds, potentially due to an antivirus tool.", STARTUP_TIME_THRESHOLD_MILLIS / 1000);

        private static final String TEXT =
            "Antivirus tools are known to substantially slow down the startup of KNIME Analytics Platform. "
                + "If you are using an antivirus tool and only installing extensions from trusted sources, "
                + "consider registering KNIME Analytics Platform as a trusted application.";

        private static final String URL = "https://www.knime.com/faq#q38";

        private static final String LINK = "See <a href=\"" + URL + "\">our FAQ</a> for more details.";

        private static final String TOGGLE_MESSAGE = "Do not show again";

        LongStartupDetectedDialog(final Display display, final DelayedMessageLogger logger) {
            super(display, logger, MessageDialog.INFORMATION, new String[]{IDialogConstants.OK_LABEL}, TITLE, SUMMARY,
                TEXT, LINK, URL, TOGGLE_MESSAGE);
        }
    }

    /**
     * @return the singleton instance of this class
     */
    public static LongStartupHandler getInstance() {
        return INSTANCE;
    }

    // the singleton instance of this class
    private static final LongStartupHandler INSTANCE = new LongStartupHandler();

    /** The amount of time (in seconds) after which startup is considered to have taken overly long. */
    private static final int STARTUP_TIME_THRESHOLD_MILLIS = 90_000;

    private final DelayedMessageLogger m_logger = new DelayedMessageLogger();

    /** The time at which the application was started. */
    private long m_timestampOnStartup;

    private boolean m_showWarn = false;

    private volatile boolean m_startupConcluded = false;

    private LongStartupHandler() {
        // singleton
    }

    /**
     * Method that should be invoked as early as possible during startup.
     *
     * @param configKey the key of the flag in the Eclipse configuration area that can be set to prevent the dialog to
     *            be shown
     * @param showDialogAndWarn if true, if startup is taking overly long, a dialog is shown and the startup time is
     *            logged as a warning; otherwise, the startup time is merely logged as a debug message
     * @param display the {@link Display}
     */
    public void onStartupCommenced(final String configKey, final boolean showDialogAndWarn, final Display display) {

        m_timestampOnStartup = System.currentTimeMillis();
        final ConfigAreaFlag flag = new ConfigAreaFlag(configKey, m_logger);

        // check if we should even show the dialog
        // check if we are on Windows
        // look up the Eclipse configuration area to determine if we should even check for Windows Defender
        if (showDialogAndWarn && Platform.OS_WIN32.equals(Platform.getOS()) && !flag.isFlagSet()) {
            m_showWarn = true;
            display.timerExec(STARTUP_TIME_THRESHOLD_MILLIS, () -> {
                if (!m_startupConcluded) {
                    final var dialog = new LongStartupDetectedDialog(display, m_logger);
                    long timestampOnOpen = System.currentTimeMillis();
                    dialog.open();
                    // while waiting for the dialog to close, we should not log startup time
                    m_timestampOnStartup += System.currentTimeMillis() - timestampOnOpen;
                    if (dialog.getToggleState()) {
                        flag.setFlag(true);
                    }
                }
            });
        }
    }

    /**
     * Method that should be invoked once startup concluded.
     */
    public void onStartupConcluded() {
        m_startupConcluded = true;

        final NodeLogger logger = NodeLogger.getLogger(LongStartupHandler.class);
        m_logger.logQueuedMessaged(logger);
        final long startupTime = (System.currentTimeMillis() - m_timestampOnStartup);
        final String startupTimeMsg = String.format("Startup took %d milliseconds.", startupTime);

        if (m_showWarn && startupTime >= STARTUP_TIME_THRESHOLD_MILLIS) {
            logger.warn(startupTimeMsg);
        } else {
            logger.debug(startupTimeMsg);
        }
    }
}
