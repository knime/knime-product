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

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.splash.BasicSplashHandler;
import org.osgi.framework.Version;

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

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(final Shell splash) {
        // Store the shell
        super.init(splash);
        setForeground(KNIME_GRAY);
        splash.setLayout(null);
        // Force shell to inherit the splash background
        splash.setBackgroundMode(SWT.INHERIT_DEFAULT);
        // workaround for AP-23264: old friend splash screen upside down on macOS 15 (Sequoia)
        if (needsFlip()) {
            flipBackgroundImage(splash);
        }
        initProgressBar();
        doEventLoop();
    }

    private static boolean needsFlip() {
        /* Eclipse 4.31 flips on macOS 14 or later
         * (https://github.com/eclipse-platform/eclipse.platform.ui/commit/477da97df613dfb0ba190876387c4407b03355c8),
         * but macOS 15 fixed the underlying bug, so now we end up with a flipped splash screen again.
         * Can be removed once we update to Eclipse 2024-09 (Eclipse 4.33) or later,
         * where their fix only applies to Sonoma
         * (https://github.com/eclipse-platform/eclipse.platform.ui/commit/26102edfcafec5c8c42949989be6833699790740).
         */
        // best effort, we bail if there are any problems

        final var ev = getEclipsePlatformVersion();
        // affected: Eclipse 4.31 and 4.32, not affected Eclipse 4.33
        final var incl = new Version(4, 31, 0);
        final var excl = new Version(4, 33, 0);
        // determine if we are using an affected Eclipse version, if not, we don't need to check the macOS version
        if (!(ev == null || (ev.compareTo(incl) >= 0 && ev.compareTo(excl) < 0))) {
            return false;
        }

        final var prop = System.getProperty("os.version");
        if (prop == null) {
            // don't know which OS version we are on (strange...), so we assume we don't need to flip
            return false;
        }
        try {
            // "fix" includes macOS 15 and above too, incorrectly, so we have to correct these
            return Integer.parseInt(prop.split("\\.")[0]) >= 15;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    /**
     * Gets the current Eclipse platform version, e.g. "4.31.100.v20240229-0520".
     * @return Current Eclipse version or {@code null} if there was any problem determining it
     */
    private static Version getEclipsePlatformVersion() {
        final var product = "org.eclipse.platform.ide";
        return Optional //
            .ofNullable(Platform.getExtensionRegistry().getExtensionPoint("org.eclipse.core.runtime.products")) //
            .map(xp -> Arrays.stream(xp.getExtensions())) //
            .map(exts -> exts.filter(ext -> product.equals(ext.getUniqueIdentifier()))).flatMap(Stream::findFirst) //
            .map(ext -> ext.getContributor()) //
            .map(contrib -> Platform.getBundle(contrib.getName())) //
            .map(bundle -> bundle.getVersion()).orElse(null); //
    }

    private static void flipBackgroundImage(final Shell splash) {
        final var flippedImage = flip(splash.getDisplay(), splash.getBackgroundImage());
        splash.setBackgroundImage(flippedImage);
        // we need to make sure the resource (i.e. image) we allocated gets disposed
        splash.addDisposeListener(e -> flippedImage.dispose());
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
        // flip down
        final var t = new Transform(display);
        t.setElements(1, 0, 0, -1, 0, 0);
        gc.setTransform(t);
        // draw moved up
        gc.drawImage(srcImage, 0, -height);
        gc.dispose();
        t.dispose();
        return target;
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
}
