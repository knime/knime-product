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
 *   9 Apr 2020 ("Marc Bux, KNIME GmbH, Berlin, Germany"): created
 */
package org.knime.product.rcp;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.knime.core.node.NodeLogger;
import org.knime.core.ui.util.SWTUtilities;

/**
 * A class that provides a method for checking for an adding an exception to Windows Defender.
 *
 * @author "Marc Bux, KNIME GmbH, Berlin, Germany"
 */
final class WindowsDefenderExceptionHandler {

    /**
     * A helper class for consuming the output stream of our executed PowerShell commands and providing it as a
     * {@link List} of {@link String Strings}.
     */
    private static class StreamGobbler implements Runnable {

        private final InputStream m_inputStream;

        private final List<String> m_output = new ArrayList<>();

        StreamGobbler(final InputStream inputStream) {
            m_inputStream = inputStream;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(m_inputStream)).lines().forEach(m_output::add);
        }

        List<String> getOutput() {
            return m_output;
        }
    }

    /**
     * The dialog that displays when Windows Defender was detected during startup.
     */
    private class WindowsDefenderDetectedDialog extends MessageDialogWithToggle {

        private static final String TITLE = "Accelerate Startup of KNIME Analytics Platform";

        private static final String URL = "https://www.knime.com/faq#q38";

        private static final String MESSAGE = "We have detected that your system is running Windows Defender, "
            + "which is known to substantially slow down the startup of KNIME Analytics Platform. "
            + "If you are only installing extensions from trusted sources, "
            + "consider adding an exception to Windows Defender to accelerate your startup. " + "See <a href=\"" + URL
            + "\">our FAQ</a> for details and instructions on how to do this manually."
            + "\n\nAlternatively, would you like knime.exe to be automatically registered "
            + "as a trusted exception with Windows Defender?";

        WindowsDefenderDetectedDialog() {
            super(SWTUtilities.getActiveShell(), TITLE, null, MESSAGE, MessageDialog.QUESTION,
                new String[]{IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL}, 0, "Do not ask again", false);
            setShellStyle(SWT.SHEET);
        }

        @Override
        protected Control createMessageArea(final Composite composite) {
            // mostly copied over from super.createMessageArea(composite), but with added link support
            // create composite
            // create image
            final Image image = getImage();
            if (image != null) {
                imageLabel = new Label(composite, SWT.NULL);
                image.setBackground(imageLabel.getBackground());
                imageLabel.setImage(image);
                GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.BEGINNING).applyTo(imageLabel);
            }
            // create message
            final Link link = new Link(composite, getMessageLabelStyle());
            link.setText(MESSAGE);
            link.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(final SelectionEvent e) {
                    try {
                        // open default external browser
                        // PlatformUI.getWorkbench().getBrowserSupport() in't an option as we don't have a workbench yet
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(new URI(URL));
                        }
                    } catch (IOException | URISyntaxException ex) {
                        queueError("Could not open external browser.", ex);
                    }
                }
            });
            GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING).grab(true, false)
                .hint(convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH), SWT.DEFAULT)
                .applyTo(link);
            return composite;
        }
    }

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

    // the singleton instance of this class
    private static final WindowsDefenderExceptionHandler INSTANCE = new WindowsDefenderExceptionHandler();

    // the name of the file placed in the Eclipse configuration area that holds any required settings
    private static final String CONFIG_NAME = "check-defender-on-startup";

    // the maximum amount of time (in seconds) until a PowerShell command times out
    private static final int COMMAND_TIMEOUT = 30;

    // the executor service for running PowerShell commands
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    // a queue of to-be-logged messages
    private final List<LogMessage> LOG = new ArrayList<>();

    private WindowsDefenderExceptionHandler() {
        // singleton
    }

    static WindowsDefenderExceptionHandler getInstance() {
        return INSTANCE;
    }

    void checkForAndAddExceptionToWindowsDefender() {
        try {
            // check if we are on Windows 10
            if (!Platform.OS_WIN32.equals(Platform.getOS()) || !System.getProperty("os.name").equals("Windows 10")) {
                return;
            }

            // look up the Eclipse configuration area to determine if we should even check for Windows Defender
            if (!isCheckForDefenderOnStartup()) {
                return;
            }

            // run the PowerShell command Get-MpComputerStatus to determine if Windows Defender is enabled
            if (!isDefenderEnabled()) {
                return;
            }

            // run the PowerShell command Get-MpPreference to check if an exception to Defender has already been set
            if (isExceptionSet()) {
                return;
            }

            // only if we went through all the checks above will we show the dialog
            final WindowsDefenderDetectedDialog dialog = new WindowsDefenderDetectedDialog();
            dialog.open();
            if (dialog.getToggleState()) {
                setCheckForDefenderOnStartup(false);
            }
            if (dialog.getReturnCode() == IDialogConstants.YES_ID) {
                // run the PowerShell command Add-MpPreference elevated to an an exception to Windows Defender
                addException();
            }

        } catch (RuntimeException e) {
            // when a runtime exception occurs, do not cancel startup but merely log the error
            queueError("Runtime exception while checking for and adding an exception to Windows Defender.", e);

        } finally {
            // defer logging until the end of execution, since it will entail Windows Defender scanning org.knime.core
            logQueuedMessaged();
        }
    }

    private void queueDebug(final String message) {
        LOG.add(new LogMessage(LogMessageLevel.DEBUG, message, null));
    }

    private void queueError(final String message) {
        LOG.add(new LogMessage(LogMessageLevel.ERROR, message, null));
    }

    private void queueError(final String message, final Exception e) {
        LOG.add(new LogMessage(LogMessageLevel.ERROR, message, e));
    }

    private void logQueuedMessaged() {
        final NodeLogger logger = NodeLogger.getLogger(WindowsDefenderExceptionHandler.class);
        for (LogMessage logMessage : LOG) {
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

    private boolean isCheckForDefenderOnStartup() {
        try {
            final Path path = getConfigPath();
            if (!Files.exists(path)) {
                // by default, do check for the Windows Defender on startup
                return true;
            }
            try (final BufferedReader reader = Files.newBufferedReader(path)) {
                return Boolean.parseBoolean(reader.readLine());
            }
        } catch (final IOException e) {
            queueError(String.format("Error when reading %s settings from configuration area.", CONFIG_NAME), e);
            // when an error occurs, do not check for the Windows Defender on startup
            return false;
        }
    }

    private void setCheckForDefenderOnStartup(final boolean checkForDefenderOnStartup) {
        try {
            final Path path = getConfigPath();
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
            }
            final byte[] bytes = Boolean.toString(checkForDefenderOnStartup).getBytes();
            Files.write(path, bytes);
        } catch (final IOException e) {
            queueError(String.format("Error when writing %s settings to configuration area.", CONFIG_NAME), e);
        }
    }

    private Path getConfigPath() throws IOException {

        final Location configLocation = Platform.getConfigurationLocation();
        if (configLocation == null) {
            throw new IOException("No configuration area set.");
        }

        final Optional<Path> configPath = getPathFromLocation(configLocation);
        if (configPath.isPresent()) {
            return configPath.get().resolve(getClass().getPackage().getName())
                .resolve(CONFIG_NAME);
        } else {
            throw new IOException("Configuration path cannot be resolved.");
        }
    }

    private static Optional<Path> getPathFromLocation(final Location location) {

        final URL configURL = location.getURL();
        if (configURL == null) {
            return Optional.empty();
        }

        String path = configURL.getPath();
        if (path.matches("^/[a-zA-Z]:/.*")) {
            // Windows path with drive letter => remove first slash
            path = path.substring(1);
        }
        return Optional.of(Paths.get(path));
    }

    /**
     * Run the PowerShell command Get-MpComputerStatus to determine if Windows Defender is enabled.
     *
     * @return true if Windows Defender is enabled, false otherwise
     */
    private boolean isDefenderEnabled() {

        final Optional<List<String>> antiMalwareServiceEnabled =
            executePowerShellCommand("Get-MpComputerStatus", "AMServiceEnabled", false);
        final Optional<List<String>> realTimeProtectionEnabled =
            executePowerShellCommand("Get-MpComputerStatus", "RealTimeProtectionEnabled", false);

        // when an error occurs, assume Windows Defender is disabled
        if (!antiMalwareServiceEnabled.isPresent() || !realTimeProtectionEnabled.isPresent()) {
            return false;
        }

        return antiMalwareServiceEnabled.get().size() == 1
            && Boolean.parseBoolean(antiMalwareServiceEnabled.get().iterator().next())
            && realTimeProtectionEnabled.get().size() == 1
            && Boolean.parseBoolean(realTimeProtectionEnabled.get().iterator().next());
    }

    /**
     * Run the PowerShell command Get-MpPreference to check if an exception to Defender has already been set.
     *
     * @return true if an exception to Windows Defender has already been set, false otherwise
     */
    private boolean isExceptionSet() {

        final Optional<List<String>> exclusionProcesses =
            executePowerShellCommand("Get-MpPreference", "ExclusionProcess", false);
        final Optional<List<String>> exclusionPaths =
            executePowerShellCommand("Get-MpPreference", "ExclusionPath", false);

        // when an error occurred, assume an exception has already been set
        if (!exclusionProcesses.isPresent() || !exclusionPaths.isPresent()) {
            return true;
        }

        // check if an exception to the knime.exe process has been added
        if (exclusionProcesses.get().contains("knime.exe")) {
            return true;
        }

        // check if an exception to the installation folder has been added
        final Location installLocation = Platform.getInstallLocation();
        if (installLocation != null) {
            final Optional<Path> installPath = getPathFromLocation(installLocation);
            if (installPath.isPresent()
                && exclusionPaths.get().stream().map(Paths::get).anyMatch(p -> installPath.get().startsWith(p))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Run the PowerShell command Add-MpPreference elevated to an an exception to Windows Defender.
     */
    private void addException() {
        executePowerShellCommand("Add-MpPreference", null, true, "`-ExclusionProcess", "knime.exe");
    }

    /**
     * Runs a PowerShell command, waits for it to terminate. If the command terminated successfully, return its output
     * or a selected property thereof. Otherwise, handle its errors.
     *
     * @param command the command that shall be run
     * @param selectProperty the property to be selected and expanded from the standard output of the PowerShell
     *            command, or null, if the whole output is to be returned
     * @param elevated a flag that determines whether to run the command elevated (i.e. with root access, triggering
     *            Windows User Account Control)
     * @param arguments the arguments for the command
     * @return the output of the PowerShell command or a selected property thereof, if the process terminated
     *         successfully, otherwise an empty Optional
     */
    private Optional<List<String>> executePowerShellCommand(final String command, final String selectProperty,
        final boolean elevated, final String... arguments) {
        try {
            final StringBuilder commandBuilder =
                new StringBuilder("powershell -inputformat none -outputformat text -NonInteractive -Command ");
            if (elevated) {
                commandBuilder.append("Start-Process powershell -Verb runAs -ArgumentList "
                    + "`-inputformat,none,`-outputformat,text,`-NonInteractive,`-Command,");
            }
            commandBuilder.append(command);
            if (arguments.length > 0) {
                if (elevated) {
                    commandBuilder.append(",");
                } else {
                    commandBuilder.append(" -ArgumentList ");
                }
                commandBuilder.append(String.join(",", arguments));
            }
            if (selectProperty != null) {
                commandBuilder.append(" | select -ExpandProperty ");
                commandBuilder.append(selectProperty);
            }
            final String fullCommand = commandBuilder.toString();
            queueDebug("Executing PowerShell command");
            queueDebug(fullCommand);

            final Process process = Runtime.getRuntime().exec(fullCommand);
            final StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream());
            EXECUTOR.submit(streamGobbler);
            final boolean timeout = !process.waitFor(COMMAND_TIMEOUT, TimeUnit.SECONDS);
            if (timeout) {
                queueError(String.format("PowerShell command %s timed out.", command));
                queueDebug("Output is:");
                streamGobbler.getOutput().stream().forEach(this::queueDebug);
            } else if (process.exitValue() == 0) {
                return Optional.of(streamGobbler.getOutput());
            } else {
                queueError(String.format("PowerShell command %s did not terminate successfully.", command));
                queueDebug("Output is:");
                streamGobbler.getOutput().stream().forEach(this::queueDebug);
            }

        } catch (IOException e) {
            queueError(String.format("I/O error occured while executing PowerShell command %s.", command), e);
        } catch (InterruptedException e) {
            queueError(String.format("Thread was interrupted while waiting for PowerShell command %s.", command), e);
            // interrupt thread, as otherwise the information that the thread was interrupted would be lost
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

}
