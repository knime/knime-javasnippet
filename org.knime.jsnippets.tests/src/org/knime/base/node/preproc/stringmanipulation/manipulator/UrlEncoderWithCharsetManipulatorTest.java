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
 *   2 Apr 2020 (carlwitt): created
 */
package org.knime.base.node.preproc.stringmanipulation.manipulator;

import static org.junit.Assert.assertNotNull;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 *
 * Tests that the standard encodings are available and the manipulator does not return null.
 *
 * Some examples of encoding differences
 *
 * All encodings
 * the space between => the+space+between
 *
 * US-ASCII, UTF-8, ISO-8859-1
 * 1 + 1 = 2 => 1+%2B+1+%3D+2
 * UTF-16BE, UTF-16LE
 * 1 + 1 = 2 => 1+%00%2B+1+%00%3D+2
 * UTF-16
 * 1 + 1 = 2 => 1+%FE%FF%00%2B+1+%FE%FF%00%3D+2
 *
 * US-ASCII, ISO-8859-1, UTF-8
 * What's the time? => What%27s+the+time%3F
 * UTF-16BE
 * What's the time? => What%00%27s+the+time%00%3F
 * UTF-16LE
 * What's the time? => What%27%00s+the+time%3F%00
 * UTF-16
 * What's the time? => What%FE%FF%00%27s+the+time%FE%FF%00%3F
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */

@RunWith(Parameterized.class)
public final class UrlEncoderWithCharsetManipulatorTest {

    /**
     * @return the standard char set names to test for availability (their availability is promised in the string
     *         manipulator's documentation).
     */
    @Parameters
    public static Iterable<String> getParameters(){
        return Arrays.asList("US-ASCII", "ISO-8859-1", "UTF-8", "UTF-16BE", "UTF-16LE", "UTF-16");
    }

    private final String m_charset;

    /**
     * @param charset
     */
    public UrlEncoderWithCharsetManipulatorTest(final String charset) {
        m_charset = charset;
    }

    /**
     * Test that the standard encodings are available (otherwise the result would be null).
     */
    @Test
    public void testEncodingsAreAvailable() {

        String input = "some input + some other = another";
        assertNotNull(UrlEncoderWithCharsetManipulator.urlEncodeCharset(input, m_charset));

    }

}
