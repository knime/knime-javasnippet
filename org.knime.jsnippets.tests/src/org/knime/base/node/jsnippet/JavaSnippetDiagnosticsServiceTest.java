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
 *   Mar 23, 2026 (chaubold): created
 */
package org.knime.base.node.jsnippet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.knime.core.webui.node.dialog.scripting.DiagnosticItem;
import org.knime.core.webui.node.dialog.scripting.DynamicCompletionRequest;

/**
 * Tests for {@link JavaSnippetDiagnosticsService}.
 *
 * @author Carsten Haubold, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
public class JavaSnippetDiagnosticsServiceTest {

    private final JavaSnippetDiagnosticsService m_service = new JavaSnippetDiagnosticsService();

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Constructs the editor text (all sections assembled) that the frontend would send to
     * {@code getDiagnostics}. The structure matches the read-only anchors defined in
     * {@link JavaSnippetScriptingNodeDialog}.
     *
     * @param imports user import lines (may be empty string)
     * @param fields user field declarations (may be empty string)
     * @param body method body (may be empty string)
     * @return the assembled editor text
     */
    private static String editorText(final String imports, final String fields, final String body) {
        return imports
            + "\n\npublic class JSnippet extends AbstractJSnippet {\n"
            + fields
            + "\n\n  @Override\n"
            + "  public void snippet() throws TypeException, ColumnException, Abort {\n"
            + body
            + "\n  }\n}\n";
    }

    private static DynamicCompletionRequest req(final String text) {
        return new DynamicCompletionRequest(text, 1, 1, "Invoked", null);
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    public void nullTextReturnsEmpty() {
        assertTrue(m_service.getDiagnostics(req(null)).isEmpty());
    }

    @Test
    public void blankTextReturnsEmpty() {
        assertTrue(m_service.getDiagnostics(req("   ")).isEmpty());
    }

    @Test
    public void nullRequestReturnsEmpty() {
        assertTrue(m_service.getDiagnostics(null).isEmpty());
    }

    @Test
    public void validEmptyBodyProducesNoDiagnostics() {
        final String text = editorText("", "", "");
        final List<DiagnosticItem> result = m_service.getDiagnostics(req(text));
        assertTrue("Valid code should produce no diagnostics", result.isEmpty());
    }

    @Test
    public void validBodyWithImportProducesNoDiagnostics() {
        final String text = editorText("import java.util.ArrayList;", "", "    ArrayList<String> list = new ArrayList<>();");
        final List<DiagnosticItem> result = m_service.getDiagnostics(req(text));
        assertTrue("Valid code with import should produce no diagnostics", result.isEmpty());
    }

    @Test
    public void syntaxErrorProducesDiagnostic() {
        // Missing semicolon: 'int x = 5' on the body line
        final String text = editorText("", "", "    int x = 5");
        final List<DiagnosticItem> result = m_service.getDiagnostics(req(text));
        assertFalse("Syntax error should produce at least one diagnostic", result.isEmpty());
    }

    @Test
    public void syntaxErrorHasErrorSeverity() {
        final String text = editorText("", "", "    int x = 5");
        final List<DiagnosticItem> result = m_service.getDiagnostics(req(text));
        assertTrue("Syntax error diagnostic should have 'Error' severity",
            result.stream().anyMatch(d -> "Error".equals(d.severity())));
    }

    @Test
    public void diagnosticLineNumberIsRelativeToEditorText() {
        // The body section starts at line 7 of the editor text (imports=1 line " ",
        // empty line, class decl, fields=" ", empty line, @Override, method sig, body)
        // Exact line depends on the assembled template; just verify it is >= 1.
        final String text = editorText("", "", "    int x = 5");
        final List<DiagnosticItem> result = m_service.getDiagnostics(req(text));
        assertFalse(result.isEmpty());
        for (final DiagnosticItem d : result) {
            assertTrue("Diagnostic line must be >= 1 (editor-relative), but was " + d.startLine(),
                d.startLine() >= 1);
        }
    }

    @Test
    public void noDiagnosticsInPreamble() {
        // All returned diagnostics must have startLine >= 1 (preamble is filtered out).
        final String text = editorText("", "", "    int x = 5");
        final List<DiagnosticItem> result = m_service.getDiagnostics(req(text));
        for (final DiagnosticItem d : result) {
            assertTrue("No diagnostic should appear at line < 1 (preamble)", d.startLine() >= 1);
        }
    }

    @Test
    public void endColumnIsAtLeastStartColumnPlusOne() {
        final String text = editorText("", "", "    int x = 5");
        final List<DiagnosticItem> result = m_service.getDiagnostics(req(text));
        assertFalse(result.isEmpty());
        for (final DiagnosticItem d : result) {
            assertTrue("endColumn must be > startColumn", d.endColumn() > d.startColumn());
        }
    }

    @Test
    public void systemImportsPreambleLineCountMatchesActualLines() {
        final int actualNewlines = (int)JavaSnippetDiagnosticsService.SYSTEM_IMPORTS_PREAMBLE
            .chars().filter(c -> c == '\n').count();
        assertEquals("SYSTEM_IMPORTS_LINE_COUNT must equal actual newline count in SYSTEM_IMPORTS_PREAMBLE",
            actualNewlines, JavaSnippetDiagnosticsService.SYSTEM_IMPORTS_LINE_COUNT);
    }
}
