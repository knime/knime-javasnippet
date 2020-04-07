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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Escapes characters that are not allowed in an URL by replacing them with ASCII characters using
 * {@link URLEncoder#encode(String, String)} and the UTF-8 character set.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 * @since 4.2
 */
public class UrlEncoderManipulator extends AbstractDefaultToStringManipulator {

    private final static String utf8 = StandardCharsets.UTF_8.name();
    /**
     * Replaces characters not allowed in an URL by ASCII characters.

     * @param str the string
     * @return the escaped string
     */
    public static String urlEncode(final String str) {
        try {
            return URLEncoder.encode(str, utf8);
        } catch (UnsupportedEncodingException e) {
            return null;
        }
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
        return "urlEncode";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return getName() + "(str)";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrArgs() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Replaces characters that are not allowed in an URL. <br/><br/>" +
                "The resulting string is percent encoded, i.e., " +
                "non-alphanumeric values are replaced as shown below. The resulting string is safe to use in a HTTP " +
                "POST request, as it would be for instance when sending data via an HTML form "
                + "(application/x-www-form-urlencoded format). " +
                "The method uses the UTF-8 encoding scheme to obtain the bytes for unsafe characters." +
                "<br/><br/>" +
                "<strong>" +
                "Examples:</strong>" +
                "<br/>" +
                "<table>" +
                "    <tr>" +
                "        <td>" +
                "        urlEncode(\"the space between\")</td>" +
                "        <td>" +
                "        =&nbsp;\"the+space+between\"</td>" +
                "    </tr>" +
                "    <tr>" +
                "        <td>" +
                "        urlEncode(\"1 + 1 = 2\")</td>" +
                "        <td>" +
                "        =&nbsp;\"1+%2B+1+%3D+2\"</td>" +
                "    </tr>" +
                "    <tr>" +
                "        <td>" +
                "        urlEncode(\"What's the time?\")</td>" +
                "        <td>" +
                "        =&nbsp;\"What%27s+the+time%3F\"</td>" +
                "    </tr>" +
                "</table>";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> getReturnType() {
        return String.class;
    }
}
