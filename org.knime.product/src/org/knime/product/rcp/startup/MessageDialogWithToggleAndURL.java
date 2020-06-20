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

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.knime.core.ui.util.SWTUtilities;

/**
 * A message dialog with toggle that can display URLs and open these URLs in an external web browser.
 *
 * @author Marc Bux, KNIME GmbH, Berlin, Germany
 */
class MessageDialogWithToggleAndURL extends MessageDialogWithToggle {

    private final String m_url;

    private final String m_text;

    private final DelayedMessageLogger m_logger;

    MessageDialogWithToggleAndURL(final String title, final String url, final String text,
        final DelayedMessageLogger logger, final int dialogImageType, final String[] dialogButtonLabels) {
        super(SWTUtilities.getActiveShell(), title, null, text, dialogImageType, dialogButtonLabels, 0,
            "Do not ask again", false);
        setShellStyle(SWT.SHEET);
        m_url = url;
        m_text = text;
        m_logger = logger;
    }

    @Override
    protected final Control createMessageArea(final Composite composite) {
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
        link.setText(m_text);
        link.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                try {
                    // open default external browser
                    // PlatformUI.getWorkbench().getBrowserSupport() in't an option as we don't have a workbench yet
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(new URI(m_url));
                    }
                } catch (IOException | URISyntaxException ex) {
                    m_logger.queueError(String.format("Could not open external browser at location \"%s\".", m_url),
                        ex);
                }
            }
        });
        GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING).grab(true, false)
            .hint(convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH), SWT.DEFAULT)
            .applyTo(link);
        return composite;
    }
}
