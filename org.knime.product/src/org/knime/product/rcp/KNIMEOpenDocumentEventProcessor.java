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
 *   Mar 14, 2016 (albrecht): created
 */
package org.knime.product.rcp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.knime.workbench.explorer.view.actions.OpenKNIMEArchiveFileAction;
import org.knime.workbench.explorer.view.actions.OpenKnimeUrlAction;

/**
 * {@link Listener} implementation to allow the opening of KNIME application files (*.knar or *.knwf).
 *
 * @author Christian Albrecht, KNIME GmbH, Konstanz, Germany
 */
public class KNIMEOpenDocumentEventProcessor implements Listener {

    private Deque<String> m_filesToOpen = new ArrayDeque<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleEvent(final Event event) {
        if (event.text != null) {
            m_filesToOpen.addLast(event.text);
        }
    }

    /**
     * If there are files to open from an openFile-Event this method triggers the corresponding Actions, depending on
     * the type of files (knimeURL or knar/knwf).
     *
     */
    public void openFiles() {
        if (!OpenKnimeUrlAction.isEventHandlingActive() || m_filesToOpen.isEmpty()) {
            // Nothing to do or Classic UI not active, clear events
            m_filesToOpen.clear();
            return;
        }

        final List<File> archiveFileList = new ArrayList<>();
        final List<String> urlFileList = new ArrayList<>();
        while (!m_filesToOpen.isEmpty()) {
            final var input = m_filesToOpen.removeFirst();
            if (KNIMEOpenUrlEventProcessor.isKnimeUrl(input)) {
                urlFileList.add(input);
                continue;
            }

            final var fileToOpen = Path.of(input);
            if (Files.isReadable(fileToOpen)) {
                if (input.endsWith(".knimeURL")) {
                    readAndDelete(fileToOpen).ifPresent(m_filesToOpen::add);
                } else {
                    archiveFileList.add(fileToOpen.toFile());
                }
            }
        }

        IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        if (!urlFileList.isEmpty()) {
            new OpenKnimeUrlAction(activePage, urlFileList).run();
        }

        if (!archiveFileList.isEmpty()) {
            new OpenKNIMEArchiveFileAction(activePage, archiveFileList).run();
        }
    }

    /**
     * Reads the first (and presumably only) line of the {@code *.knimeURL} file and returns it if it is a KNIME URL.
     * The file is deleted afterwards.
     *
     * @param fileToOpen
     * @return
     */
    private static Optional<String> readAndDelete(final Path fileToOpen) {
        try {
            try (final var reader = Files.newBufferedReader(fileToOpen)) {
                final String url = reader.readLine();
                if (KNIMEOpenUrlEventProcessor.isKnimeUrl(url)) {
                    return Optional.of(url);
                }
            } finally {
                Files.delete(fileToOpen);
            }
        }  catch (IOException e) { // NOSONAR
            // TODO issue warning?
        }
        return Optional.empty();
    }
}
