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
 *
 * History
 *   04.10.2011 (hofer): created
 */
package org.knime.base.node.preproc.stringmanipulation.manipulator;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;



/**
 * A StringManipulator to perform search and replace of substrings.
 *
 * @author Heiko Hofer
 */
public class ReplaceModifiersManipulator implements Manipulator {

    /**
     * Replaces all occurrences of a String within another String..
     *
     * @param str the string
     * @param search the substring to search for
     * @param replace every occurrence of search will be replaced by this
     * @param modifiers string with binary options
     * @return the index of the first occurrence of needle in s
     */
    public static String replace(final String str,
            final String search, final String replace,
            final String modifiers) {
        if (null == str || str.isEmpty() || null == search || search.isEmpty()
                || replace == null) {
            return str;
        }
        String opt = (null != modifiers) ? modifiers.toLowerCase(Locale.ENGLISH) : "";
        boolean ignoreCase = StringUtils.contains(opt, 'i');
        boolean words = StringUtils.contains(opt, 'w');
        // create new modifiers string with allowed options
        String mdfrs = "";
        mdfrs = ignoreCase ? opt + "i" : opt;
        mdfrs = words ? opt + "w" : opt;

        int start = 0;
        int end = IndexOfModifiersManipulator.indexOf(str, search, mdfrs);
        if (end == StringUtils.INDEX_NOT_FOUND) {
            return str;
        }
        StringBuilder buf = new StringBuilder();
        while (end != StringUtils.INDEX_NOT_FOUND) {
            buf.append(str.substring(start, end)).append(replace);
            start = end + search.length();
            end = IndexOfOffsetModifiersManipulator.indexOf(str, search,
                    start, mdfrs);
        }
        buf.append(str.substring(start));
        return buf.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCategory() {
        return "Replace";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "replace";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return getName() + "(str, search, replace, modifiers)";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrArgs() {
        return 4;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Replaces all occurrences of a String within another String. "
        + " <i>modifiers</i> gives several options "
        + "to control the search:"
        + "<br/>"
        + "<table style=\"padding: 0px 0px 0px 5px;\">"
        + "<tr><td style=\"padding: 0px 8px 0px 0px;\">i</td> "
        + "<td>ignore case</td></tr>"
        + "<tr><td style=\"padding: 0px 8px 0px 0px;\">w</td> "
        + "<td>whole word (word boundaries are "
        + "whitespace characters)</td></tr>"
        + "</table>"
        + ""
        + "<br/>"
        + "<strong>Examples:</strong>"
        + "<br/>"
        + "<table>"
        + "<tr><td>replace(\"abcABC\", \"ab\", \"\", \"\")</td>"
        + "<td>=&nbsp;\"cABC\"</td></tr>"

        + "<tr><td>replace(\"abcABC\", \"ab\", \"z\", \"\")</td>"
        + "<td>=&nbsp;\"zcABC\"</td></tr>"

        + "<tr><td>replace(\"abcABC\", \"ab\", \"\", \"i\")</td>"
        + "<td>=&nbsp;\"cC\"</td></tr>"

        + "<tr><td>replace(\"abcABC\", \"ab\", \"z\", \"i\")</td>"
        + "<td>=&nbsp;\"zczC\"</td></tr>"

        + "<tr><td>replace(\"ab abAB AB\", \"ab\", \"z\", \"w\")</td>"
        + "<td>=&nbsp;\"z abAB AB\"</td></tr>"

        + "<tr><td>replace(\"ab abAB AB\", \"ab\", \"z\", \"iw\")</td>"
        + "<td>=&nbsp;\"z abAB z\"</td></tr>"

        + "<tr><td>replace(null, *, *, *)</td>"
        + "<td>=&nbsp;null</td></tr>"

        + "<tr><td>replace(\"\", *, *, *)</td>"
        + "<td>=&nbsp;\"\"</td></tr>"

        + "<tr><td>replace(\"any\", null, *, *)</td>"
        + "<td>=&nbsp;\"any\"</td></tr>"

        + "<tr><td>replace(\"any\", *, null, *)</td>"
        + "<td>=&nbsp;\"any\"</td></tr>"

        + "<tr><td>replace(\"any\", \"\", *, *)</td>"
        + "<td>=&nbsp;\"any\"</td></tr>"

        + "</table>"
        + "* can be any character sequence.<br/>";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> getReturnType() {
        return String.class;
    }
}
