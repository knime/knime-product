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
 * ------------------------------------------------------------------------
 */
package org.knime.product;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.splash.BasicSplashHandler;

/**
 * This is the dynamic splash screen for KNIME that customizes the text above the progress bar. This implementation of
 * {@link BasicSplashHandler} displayed icons added via the extension point
 * <code>org.knime.product.splashExtension</code> until version 5.0. Starting from 5.1, we do not display them anymore.
 *
 * @since 2.0
 * @author Thorsten Meinl, University of Konstanz
 */
public class KNIMESplashHandler extends BasicSplashHandler {

    private static final Rectangle PROGRESS_RECT = new Rectangle(5, 295, 445, 15);

    private static final Rectangle MESSAGE_RECT = new Rectangle(7, 272, 445, 20);

    private static final RGB KNIME_GRAY = new RGB(0x7d, 0x7d, 0x7d);

    @Override
    public void init(final Shell splash) {
        // Store the shell
        super.init(splash);

        setForeground(KNIME_GRAY);
        splash.setLayout(null);
        // Force shell to inherit the splash background
        splash.setBackgroundMode(SWT.INHERIT_DEFAULT);

        // workaround for AP-21237
        fixMacOSBackgroundFlipBug(splash);

        initProgressBar();
        doEventLoop();
    }

    private void initProgressBar() {
        setProgressRect(PROGRESS_RECT);
        setMessageRect(MESSAGE_RECT);
        getContent();
    }

    private void doEventLoop() {
        Shell splash = getSplash();
        if (!splash.getDisplay().readAndDispatch()) {
            splash.getDisplay().sleep();
        }
    }

    private static void fixMacOSBackgroundFlipBug(final Shell splash) {
        if (Platform.OS_MACOSX.equals(Platform.getOS()) && isFlipBugPresent(Display.getDefault(), splash)) {
            final var display = Display.getDefault();
            final var srcImage = splash.getBackgroundImage();
            splash.setBackgroundImage(flip(display, srcImage));
        }
    }

    private static Image flip(final Display display, final Image srcImage) {
        final var bounds = srcImage.getBounds();
        final var width = bounds.width;
        final var height = bounds.height;
        final var target = new Image(display, width, height);
        final var gc = new GC(target);
        gc.setAdvanced(true);
        gc.setAntialias(SWT.ON);
        gc.setInterpolation(SWT.HIGH);
        final var t = new Transform(display);
        t.setElements(1, 0, 0, -1, 0, 0);
        gc.setTransform(t);
        gc.drawImage(srcImage, 0, -height, bounds.width, bounds.height, 0, 0, width, height);
        gc.dispose();
        srcImage.dispose();
        return target;
    }

    /**
     * Empirically checks if the macOS Sonoma 14.0 background image flip bug is present or not.
     * See <a href="https://github.com/eclipse-platform/eclipse.platform.swt/issues/772">
     * https://github.com/eclipse-platform/eclipse.platform.swt/issues/772</a>.
     *
     * @param display current display
     * @param splash used as parent for test control
     * @return {@code true} if the flip bug is present, {@code false} otherwise
     */
    private static boolean isFlipBugPresent(final Display display, final Shell splash) {
        final var white = display.getSystemColor(SWT.COLOR_WHITE);
        final var black = display.getSystemColor(SWT.COLOR_BLACK);
        final var palette = new PaletteData(white.getRGB(), black.getRGB());
        final var data = new ImageData(1, 2, 1, palette);
        data.setPixel(0, 1, 1);
        // any Control object is enough, since the Splash screen is also a Control (and this is where the bug occurs)
        final var control = new Link(splash, SWT.NONE);
        control.setBackgroundImage(new Image(display, data));
        final var backgroundData = control.getBackgroundImage().getImageData();
        final var top = backgroundData.getPixel(0, 1);
        control.dispose();
        return top == 0;
    }
}
