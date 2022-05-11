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
package org.knime.product.p2.actions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.p2.engine.spi.ProvisioningAction;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Custom p2 action that can be used by plugins in order to execute arbitrary
 * commands during their installation.
 *
 * @author Iman Karim <iman@biosolveit.de>
 * @author Thorsten Meinl, University of Konstanz
 * @since 2.5.2
 */
public class ShellExec extends ProvisioningAction {
    private static final Bundle bundle = FrameworkUtil
            .getBundle(ShellExec.class);

    private final static ILog logger = Platform.getLog(bundle);

    @Override
    public IStatus execute(final Map<String, Object> parameters) {
        String os = null; // Operating System the Command is for. Null means
                          // all.

        if (parameters.containsKey("os")) {
            os = (String)parameters.get("os");
        }

        if (verifyOS(os)) {
            String directory = null;
            String command = null;

            if (parameters.containsKey("command")) {
                command = (String)parameters.get("command");
                logInfo("ShellExec command: %s", command);
            }
            if (command == null) {
                logError("Command is null!");
                return Status.CANCEL_STATUS;
            }

            if (parameters.containsKey("directory")) {
                directory = (String)parameters.get("directory");
                logInfo("ShellExec directory : %s", directory);
            }
            if (directory == null) {
                logError("ShellExec directory is null!");
                return Status.CANCEL_STATUS;
            }

            File dirFile = new File(directory);
            try {
                Process p = Runtime.getRuntime().exec(command, null, dirFile);
                captureOutput("Standard output", p::getInputStream);
                captureOutput("Standard error", p::getErrorStream);
                int exitVal = p.waitFor();
                if (exitVal != 0) {
                    logger.log(new Status(IStatus.ERROR, bundle
                            .getSymbolicName(),
                            "ShellExec command exited non-zero exit value"));
                    return Status.CANCEL_STATUS;
                }
            } catch (Exception e) {
                logger.log(new Status(IStatus.ERROR, bundle.getSymbolicName(),
                        "Exception occured", e));
                return Status.CANCEL_STATUS;
            }
        }
        return Status.OK_STATUS;
    }

    private static void captureOutput(final String outputType, final Supplier<InputStream> streamSupplier) {
        new Thread(() -> logIfNotBlank(outputType, consumeStream(streamSupplier))).start();
    }

    private static void logIfNotBlank(final String outputType, final String output) {
        if (!output.isBlank()) {
            logInfo("%s: %s", outputType, output);
        }
    }

    private static void logInfo(final String format, final Object ... args) {
        log(IStatus.INFO, format, args);
    }

    private static void logError(final String format, final Object ... args) {
        log(IStatus.ERROR, format, args);
    }

    private static void logError(final Throwable cause, final String format, final Object...args) {
        logger.log(new Status(IStatus.ERROR, bundle.getSymbolicName(), String.format(format, args), cause));
    }

    private static void log(final int statusType, final String format, final Object ... args) {
        logger.log(new Status(statusType, bundle.getSymbolicName(), String.format(format, args)));
    }

    private static String consumeStream(final Supplier<InputStream> streamSupplier) {
        try (var stream = streamSupplier.get()) {
            var writer = new StringWriter();
            IOUtils.copy(stream, writer, StandardCharsets.UTF_8);
            return writer.toString();
        } catch (IOException ex) {
            logError(ex, "Failed to consume input stream");
            throw new IllegalStateException(ex);
        }
    }

    private static boolean verifyOS(final String os) {
        return (os == null) || Platform.getOS().equals(os);
    }

    @Override
    public IStatus undo(final Map<String, Object> parameters) {
        return Status.OK_STATUS;
    }
}
