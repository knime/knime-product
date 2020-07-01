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

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.IProduct;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;

/**
 * A message dialog with toggle that can display URLs and open these URLs in an external web browser.
 *
 * @author Marc Bux, KNIME GmbH, Berlin, Germany
 */
class MessageDialogWithToggleAndURL extends MessageDialog {

    // copied from org.knime.core.ui.util.SWTUtilities#getActiveShell
    private static Shell getActiveShell(final Display display) {
        if (display == null) {
            return null;
        }

        final Shell shell = display.getActiveShell();

        if (shell == null) {
            final IProduct product = Platform.getProduct();
            Shell likelyActiveShell = null;

            for (Shell s : display.getShells()) {
                if (s.getText().startsWith(product.getName())) {
                    return s;
                }

                if (s.getShells().length == 0) {
                    likelyActiveShell = s;
                }
            }

            return likelyActiveShell;
        }

        return shell;
    }

    private final Display m_display;

    private final String m_summary;

    private final String m_text;

    private final String m_link;

    private final String m_url;

    private final String m_toggleMessage;

    private final DelayedMessageLogger m_logger;

    private boolean m_toggleState = false;

    MessageDialogWithToggleAndURL(final Display display, final DelayedMessageLogger logger, final int dialogImageType,
        final String[] dialogButtonLabels, final String title, final String summary, final String text,
        final String link, final String url, final String toggleMessage) {
        super(getActiveShell(display), title, null, null, dialogImageType, 0, dialogButtonLabels);
        setShellStyle(SWT.SHEET);
        m_display = display;
        m_summary = summary;
        m_text = text;
        m_link = link;
        m_url = url;
        m_toggleMessage = toggleMessage;
        m_logger = logger;
    }

    @Override
    protected final Control createMessageArea(final Composite composite) {

        // create image
        final Image image = getImage();
        if (image != null) {
            imageLabel = new Label(composite, SWT.NULL);
            image.setBackground(imageLabel.getBackground());
            imageLabel.setImage(image);
            GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.BEGINNING).applyTo(imageLabel);
        }

        // create vertical composite
        final Composite vertical = new Composite(composite, SWT.NONE);
        final GridLayout layout = new GridLayout();
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        layout.verticalSpacing = 25;
        vertical.setLayout(layout);
        GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING).applyTo(vertical);

        final Label summary = new Label(vertical, getMessageLabelStyle());
        final FontData[] fd = summary.getFont().getFontData();
        for (FontData f : fd) {
            f.setStyle(SWT.BOLD);
        }
        summary.setFont(new Font(m_display, fd));
        summary.setText(m_summary);
        GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING).grab(true, false)
            .hint(convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH), SWT.DEFAULT)
            .applyTo(summary);

        final Label text = new Label(vertical, getMessageLabelStyle());
        text.setText(m_text);
        GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING).grab(true, false)
            .hint(convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH), SWT.DEFAULT)
            .applyTo(text);

        // create message
        final Link link = new Link(vertical, getMessageLabelStyle());
        link.setText(m_link);
        link.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                // code inspired by org.knime.core.util.DesktopUtil#browse
                Display.getDefault().asyncExec(() -> {
                    //try a normal launch
                    if (!Program.launch(m_url) && Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        try {
                            // we cannot use PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL
                            // as we don't have a workbench yet
                            Desktop.getDesktop().browse(new URI(m_url));
                        } catch (IOException | URISyntaxException ex) {
                            m_logger.queueError(
                                String.format("Error when trying to open external browser at location \"%s\".", m_url),
                                ex);
                        }
                    } else {
                        m_logger
                            .queueError(String.format("Could not open external browser at location \"%s\".", m_url));
                    }
                });
            }
        });
        GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING).grab(true, false)
            .hint(convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH), SWT.DEFAULT)
            .applyTo(link);

        if (m_toggleMessage != null) {
            final Button button = new Button(vertical, SWT.CHECK | SWT.LEFT);
            button.setText(m_toggleMessage);
            button.setSelection(m_toggleState);
            button.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(final SelectionEvent e) {
                    m_toggleState = button.getSelection();
                }
            });
        }

        return composite;
    }

    boolean getToggleState() {
        return m_toggleState;
    }
}
