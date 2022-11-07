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
 *   8 Nov 2022 (manuelhotz): created
 */
package org.knime.product.rcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests the {@code IniChangedChecker}'s digest and detection methods.
 *
 * @author Manuel Hotz &lt;manuel.hotz@knime.com&gt;
 */
public class IniChangedCheckerTest {

    /**
     * Temporary directory used for test.
     */
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Tests that the checker correctly detects changes to the monitored file.
     * @throws IOException I/O while writing test file or reading in checker
     */
    @Test
    public void testIniChanged() throws IOException {
        final var f = tmp.newFile();
        try (final var os = new FileOutputStream(f)) {
            os.write("BEFORE_CONTENTS".getBytes());
        }
        final var c = new IniChangedChecker(Paths.get(f.toURI()));
        c.digestIni();
        assertFalse("File contents did not yet change, but change detected", c.iniDidChange());
        try (final var os = new FileOutputStream(f)) {
            os.write("AFTER_CONTENTS".getBytes());
        }
        c.digestIni();
        assertTrue("File contents did change, but change not detected", c.iniDidChange());
        try (final var os = new FileOutputStream(f)) {
            os.write("FINAL_CONTENTS".getBytes());
        }
        c.digestIni();
        assertTrue("File contents did change again, but change not detected", c.iniDidChange());
    }

    /**
     * Tests that the checker correctly detects that the file did not change after digesting more than twice.
     * @throws IOException I/O while writing test file or reading in checker
     */
    @Test
    public void testIniNotChanged() throws IOException {
        final var f = tmp.newFile();
        try (final var os = new FileOutputStream(f)) {
            os.write("BEFORE_CONTENTS".getBytes());
        }
        final var c = new IniChangedChecker(Paths.get(f.toURI()));
        assertFalse("File not yet digested, but change detected", c.iniDidChange());
        c.digestIni();
        assertFalse("File digested only once, but change detected", c.iniDidChange());
        c.digestIni();
        assertFalse("File contents did not change, but change detected", c.iniDidChange());
        c.digestIni();
        assertFalse("File contents did not change, but change detected", c.iniDidChange());
    }

    /**
     * Tests that an ini that goes away is not detected as changed. In this case, we do not want to prompt the user
     * to restart the workbench.
     * @throws IOException I/O while writing test file or reading in checker
     */
    @Test
    public void testIniBecameInvalid() throws IOException {
        final var f = tmp.newFile();
        try (final var os = new FileOutputStream(f)) {
            os.write("BEFORE_CONTENTS".getBytes());
        }
        final var c = new IniChangedChecker(Paths.get(f.toURI()));
        c.digestIni();
        f.delete();
        c.digestIni();
        assertFalse("Ini deleted, but content change detected", c.iniDidChange());
    }

    /**
     * Tests that an ini which becomes valid is detected as changed.
     * @throws IOException I/O while writing test file or reading in checker
     */
    @Test
    public void testIniBecameValid() throws IOException {
        final var f = tmp.newFile();
        f.delete();
        final var c = new IniChangedChecker(Paths.get(f.toURI()));
        c.digestIni();
        assertFalse("Nonexisting ini detected as changed content", c.iniDidChange());
        c.digestIni();
        assertFalse("Nonexisting ini detected as changed content after repeated digest", c.iniDidChange());

        f.createNewFile();
        try (final var os = new FileOutputStream(f)) {
            os.write("BEFORE_CONTENTS".getBytes());
        }
        c.digestIni();
        assertTrue("Ini became available, but no content change detected", c.iniDidChange());
    }

    /**
     * Tests that an effectively disabled checker (no file to check given) never reports changes.
     * @throws IOException I/O while writing test file or reading in checker
     */
    @Test
    public void testDisabledChecker() throws IOException {
        final var c = new IniChangedChecker(null);
        c.digestIni();
        c.digestIni();
        assertFalse("Reported change but there is no file to check", c.iniDidChange());
        c.digestIni();
        assertFalse("Reported change but there is no file to check", c.iniDidChange());
    }

    /**
     * Tests that the checker correctly digests the stream contents given an arbitrary algorithm.
     * @throws NoSuchAlgorithmException when the hash algorithm is not found
     * @throws IOException I/O while reading test data
     */
    @Test
    public void testDigestFile() throws NoSuchAlgorithmException, IOException {
        final MessageDigest md = MessageDigest.getInstance("MD5");
        final var ref = "AC35ACAF1BA2AFBFD95B0FD69D6D58CD";

        try (final var in = new ByteArrayInputStream("KNIME".getBytes())) {
            final var digest = IniChangedChecker.digestFile(in, md);
            final var sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02X", b));
            }
            // waiting for Java 17 HexFormat for easy parsing of reference above...
            assertEquals("Checker did not correctly digest the given file contents", ref, sb.toString());
        }
    }
}
