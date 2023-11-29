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
package org.knime.product.rcp.startup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.swt.widgets.Display;
import org.knime.core.node.NodeLogger;

/**
 * A class that provides a method for checking for an adding an exception to Windows Defender.
 *
 * @author "Marc Bux, KNIME GmbH, Berlin, Germany"
 */
public final class WindowsDefenderExceptionHandler {

    /**
     * The dialog that displays when Windows Defender was detected during startup.
     */
    private static final class WindowsDefenderDetectedDialog extends MessageDialogWithToggleAndURL {

        private static final String TITLE = "Accelerate Startup of KNIME Analytics Platform";

        private static final String SUMMARY = "Would you like knime.exe to be automatically registered "
            + "as a trusted exception with Windows Defender?";

        private static final String TEXT = "We have detected that your system is running Windows Defender, "
            + "which is known to substantially slow down the startup of KNIME Analytics Platform. "
            + "If you are only installing extensions from trusted sources, "
            + "consider adding an exception to Windows Defender to accelerate your startup.";

        private static final String URL = "https://www.knime.com/faq#q38";

        private static final String LINK = "See <a href=\"" + URL + "\">our FAQ</a> for more details.";

        private static final String TOGGLE_MESSAGE = "Do not ask again";

        WindowsDefenderDetectedDialog(final Display display, final DelayedMessageLogger logger) {
            super(display, logger, MessageDialog.QUESTION,
                new String[]{IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL}, TITLE, SUMMARY, TEXT, LINK, URL,
                TOGGLE_MESSAGE);
        }
    }

    /**
     * @return the singleton instance of this class
     */
    public static WindowsDefenderExceptionHandler getInstance() {
        return INSTANCE;
    }

    // the singleton instance of this class
    private static final WindowsDefenderExceptionHandler INSTANCE = new WindowsDefenderExceptionHandler();

    // the maximum amount of time (in seconds) until a PowerShell command times out
    private static final int COMMAND_TIMEOUT = 30;

    private final DelayedMessageLogger m_logger = new DelayedMessageLogger();

    private WindowsDefenderExceptionHandler() {
        // singleton
    }

    /**
     * When on Windows 10, determine if Windows Defender is enabled. If no exception to Defender has already been set,
     * displays a dialog that informs the user about Defender slowing down the startup of KNIME AP.
     *
     * @param configKey the key of the flag in the Eclipse configuration area that determines whether to show the dialog
     * @param display display the {@link Display}
     * @return true if and only if the dialog was shown
     */
    public boolean checkForAndAddExceptionToWindowsDefender(final String configKey, final Display display) {
        try {
            final ConfigAreaFlag flag = new ConfigAreaFlag(configKey, m_logger);

            // check if we are on Windows 10
            // look up the Eclipse configuration area to determine if we should even check for Windows Defender
            // run the PowerShell command Get-MpComputerStatus to determine if Windows Defender is enabled
            // run the PowerShell command Get-MpPreference to check if an exception to Defender has already been set
            final var isWin10 = Platform.OS_WIN32.equals(Platform.getOS())
                    && System.getProperty("os.name").equals("Windows 10");
            if (isWin10 && !flag.isFlagSet() && isDefenderEnabled() && !isExceptionSet()) {

                // only if we went through all the checks above will we show the dialog
                final WindowsDefenderDetectedDialog dialog = new WindowsDefenderDetectedDialog(display, m_logger);
                dialog.open();
                if (dialog.getToggleState()) {
                    flag.setFlag(true);
                }
                if (dialog.getReturnCode() == 0) {
                    // run the PowerShell command Add-MpPreference elevated to an an exception to Windows Defender
                    addException();
                }
                return true;
            }
        } catch (RuntimeException e) {
            // when a runtime exception occurs, do not cancel startup but merely log the error
            m_logger.queueError("Runtime exception while checking for and adding an exception to Windows Defender.", e);

        } finally {
            // defer logging until the end of execution, since it will entail Windows Defender scanning org.knime.core
            m_logger.logQueuedMessaged(NodeLogger.getLogger(WindowsDefenderExceptionHandler.class));
        }

        return false;
    }

    /**
     * Run the PowerShell command Get-MpComputerStatus to determine if Windows Defender is enabled.
     *
     * @return true if Windows Defender is enabled, false otherwise
     */
    private boolean isDefenderEnabled() {

        final Optional<List<String>> antiMalwareServiceEnabled =
            executePowerShellCommand("Get-MpComputerStatus", "AMServiceEnabled", false);
        if (!antiMalwareServiceEnabled.isPresent() || antiMalwareServiceEnabled.get().size() != 1
            || !Boolean.parseBoolean(antiMalwareServiceEnabled.get().iterator().next())) {
            return false;
        }

        final Optional<List<String>> realTimeProtectionEnabled =
            executePowerShellCommand("Get-MpComputerStatus", "RealTimeProtectionEnabled", false);
        return (realTimeProtectionEnabled.isPresent() && realTimeProtectionEnabled.get().size() == 1
            && Boolean.parseBoolean(realTimeProtectionEnabled.get().iterator().next()));
    }

    /**
     * Run the PowerShell command Get-MpPreference to check if an exception to Defender has already been set.
     *
     * @return true if an exception to Windows Defender has already been set, false otherwise
     */
    private boolean isExceptionSet() {

        final Optional<List<String>> exclusionProcesses = executePowerShellCommand("Get-Item", "Property", false,
            "-Path", "'Registry::HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows Defender\\Exclusions\\Processes'");

        if (!exclusionProcesses.isPresent() || exclusionProcesses.get().contains("knime.exe")) {
            return true;
        }

        final Optional<List<String>> exclusionPaths = executePowerShellCommand("Get-Item", "Property", false, "-Path",
            "'Registry::HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows Defender\\Exclusions\\Paths'");
        // check if an exception to the knime.exe process has been added
        if (!exclusionPaths.isPresent()) {
            return true;
        }

        // check if an exception to the installation folder has been added
        final Location installLocation = Platform.getInstallLocation();
        if (installLocation != null) {
            final Optional<Path> installPath = ConfigAreaFlag.getPathFromLocation(installLocation);
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
        executePowerShellCommand("Add-MpPreference", null, true, "-ExclusionProcess", "knime.exe");
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
     * @return the output of the PowerShell command or a selected property thereof, if the process was not run elevated
     *         and it terminated successfully, otherwise an empty Optional
     */
    private Optional<List<String>> executePowerShellCommand(final String command, final String selectProperty,
        final boolean elevated, final String... arguments) {
        final List<String> commands = new ArrayList<>();
        commands.add("powershell");
        commands.add("-inputformat");
        commands.add("none");
        commands.add("-outputformat");
        commands.add("text");
        commands.add("-NonInteractive");
        commands.add("-Command");
        if (elevated) {
            commands.add("Start-Process");
            commands.add("powershell");
            commands.add("-Verb");
            commands.add("runAs");
            commands.add("-ArgumentList");
            StringBuilder sb = new StringBuilder();
            sb.append("`-inputformat,none,`-outputformat,text,`-NonInteractive,`-Command,");
            sb.append(command);
            if (arguments.length > 0) {
                sb.append(",");
                final String joinedArgs = String.join(",", arguments);
                sb.append(joinedArgs.replace("-", "`-"));
            }
            sb.append(",`-ErrorAction,Stop");
            commands.add(sb.toString());
        } else {
            commands.add(command);
            if (arguments.length > 0) {
                commands.add(String.join(" ", arguments));
            }
        }
        commands.add("-ErrorAction");
        commands.add("Stop");
        if (selectProperty != null) {
            commands.add("|");
            commands.add("Select-Object");
            commands.add("-ExpandProperty");
            commands.add(selectProperty);
        }
        m_logger.queueDebug("Executing PowerShell command");
        m_logger.queueDebug(String.join(" ", commands));

        return executePowerShellCommand(commands);
    }

    private Optional<List<String>> executePowerShellCommand(final List<String> commands) {

        final String command = String.join(" ", commands);

        final Process process;
        try {
            process = (new ProcessBuilder(commands)).start();
        } catch (IOException e) {
            m_logger.queueError(String.format("I/O error occured while executing PowerShell command %s.", command), e);
            return Optional.empty();
        }

        final List<String> stdoutRef = new ArrayList<>();
        final List<String> stderrRef = new ArrayList<>();
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(
            () -> new BufferedReader(new InputStreamReader(process.getInputStream())).lines().forEach(stdoutRef::add));
        executor.submit(
            () -> new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().forEach(stderrRef::add));
        executor.shutdown();

        try {
            if (!process.waitFor(COMMAND_TIMEOUT, TimeUnit.SECONDS)) {
                m_logger.queueError(String.format("PowerShell command %s timed out.", command));
                process.destroyForcibly().waitFor();
            }
        } catch (InterruptedException e) {
            m_logger.queueError(
                String.format("Thread was interrupted while waiting for PowerShell command %s.", command), e);
            // interrupt thread, as otherwise the information that the thread was interrupted would be lost
            Thread.currentThread().interrupt();
            return Optional.empty();
        }

        if (process.exitValue() == 0) {
            return Optional.of(stdoutRef);
        } else {
            m_logger.queueError(String.format("PowerShell command %s did not terminate successfully.", command));
            final List<String> stdout = stdoutRef;
            final List<String> stderr = stderrRef;
            if (!stdout.isEmpty()) {
                m_logger.queueError("Stdout is:");
                stdout.stream().forEach(m_logger::queueDebug);
            }
            if (!stderr.isEmpty()) {
                m_logger.queueError("Stderr is:");
                stderr.stream().forEach(m_logger::queueError);
            }
            return Optional.empty();
        }
    }
}
