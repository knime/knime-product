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
 *   7 Nov 2022 (manuelhotz): created
 */
package org.knime.product.rcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

/**
 * A class used to check if the {@code 'knime.ini'} has changed in between successive calls to {@code #digestIni()}.
 * The used checksum algorithm is only appropriate for the current use case and nothing more.
 *
 * @author Manuel Hotz &lt;manuel.hotz@knime.com&gt;
 */
final class IniChangedChecker {

    private static final byte[] INVALID = new byte[] {0xA, 0xF, 0xF, 0xE};

    private byte[] m_digestBefore;
    private byte[] m_digestAfter;
    private MessageDigest m_digester;
    private Path m_iniPath;
    private ILog m_log;

    /**
     * Creates a new checker for the given ini path and initializes the digester.
     *
     * @param iniPath the path to check ini at
     */
    IniChangedChecker(final Path iniPath) {
        m_log = Platform.getLog(Platform.getBundle("org.knime.product"));
        try {
            // md5 is enough in our case
            // NOFLUID non-sensitive usage
            m_digester = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            // effectively disable ini-change-detection feature
            m_log.log(new Status(IStatus.WARNING, "org.knime.product",
                "'MD5' message digest not available, disabling ini change detection.", e));
            m_digester = null;
        }
        m_iniPath = iniPath;
    }

    /**
     * Digest the current {@code 'knime.ini'} contents (if available).
     *
     * @return this checker
     */
    IniChangedChecker digestIni() {
        // SDK AP has no ini file
        if (m_iniPath == null || m_digester == null) {
            if (m_digestBefore == null) {
                m_digestBefore = INVALID;
            } else {
                m_digestAfter = INVALID;
            }
            return this;
        }

        // successive calls should work and should retire old after image to before image
        if (m_digestAfter != null) {
            m_digestBefore = m_digestAfter;
        }

        byte[] digest = null;
        try (final var in = Files.newInputStream(m_iniPath, StandardOpenOption.READ)) {
            digest = digestFile(in, m_digester);
        } catch (IOException e) {
            m_log.log(new Status(IStatus.ERROR, "org.knime.product",
                String.format("Error while reading ini from '%s'", m_iniPath), e));
            digest = INVALID;
        }
        if (m_digestBefore == null) {
            m_digestBefore = digest;
        } else {
            m_digestAfter = digest;
        }

        return this;
    }

    /**
     * Checks if the contents of the {@code 'knime.ini'} did change between <b>two successive</b> calls
     * to {@code #digestIni()}.
     *
     * @return {@code true} if the ini did change, {@code false} otherwise
     */
    boolean iniDidChange() {
        if (m_iniPath == null || m_digestBefore == null || m_digestAfter == null) {
            return false;
        }
        // waiting for pattern-matching... :)
        if (m_digestBefore != INVALID && m_digestAfter != INVALID) {
            // we don't need time-constant comparison, so we don't use 'MessageDigest#isEqual'
            return !Arrays.equals(m_digestBefore, m_digestAfter);
        } else {
            // Three cases remain:
            // 1) ini became valid <=> below expression is true
            // 2) ini became invalid => below expression is false
            //    (e.g. when deleted)
            //    if the ini goes away, it can still restart the workbench,
            //    since the ini is not re-read in this case
            // 3) both are invalid => below expression is false
            //    e.g. when running from SDK
            return m_digestBefore == INVALID && m_digestAfter != INVALID;
        }
    }

    static byte[] digestFile(final InputStream in, final MessageDigest md) throws IOException {
        try (final var ds = new DigestInputStream(in, md)) {
            while (ds.read() != -1) {
                // empty on purpose since we only need to read the whole stream to digest it but otherwise
                // don't have to do anything with the data
            }
        }
        return md.digest();
    }

}
