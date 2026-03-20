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
 *   Mar 18, 2026 (chaubold): created
 */
package org.knime.base.node.jsnippet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.webui.node.dialog.scripting.DynamicCompletionItem;
import org.knime.core.webui.node.dialog.scripting.DynamicCompletionRequest;
import org.knime.core.webui.node.dialog.scripting.WorkflowControl;

/**
 * Tests for {@link JavaSnippetCompletionService}.
 *
 * @author Carsten Haubold, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
public class JavaSnippetCompletionServiceTest {

    private WorkflowControl m_workflowControl;

    private JavaSnippetCompletionService m_service;

    /** Convenience helper to create empty FieldMappings. */
    private static JavaSnippetScriptingService.FieldMappings emptyMappings() {
        return new JavaSnippetScriptingService.FieldMappings(
            new JavaSnippetScriptingService.FieldMapping[0],
            new JavaSnippetScriptingService.FieldMapping[0],
            new JavaSnippetScriptingService.FieldMapping[0],
            new JavaSnippetScriptingService.FieldMapping[0]);
    }

    @Before
    public void setUp() {
        m_workflowControl = mock(WorkflowControl.class);
        // Safe defaults: empty spec and no flow variables
        when(m_workflowControl.getInputSpec()).thenReturn(new PortObjectSpec[0]);
        m_service = new JavaSnippetCompletionService(m_workflowControl, emptyMappings());
    }

    // ── Helper factories ────────────────────────────────────────────────────

    private static DynamicCompletionRequest invokedRequest(final String text, final int line, final int col) {
        return new DynamicCompletionRequest(text, line, col, "Invoked", null);
    }

    private static DynamicCompletionRequest dotTriggerRequest(final String text, final int line, final int col) {
        return new DynamicCompletionRequest(text, line, col, "TriggerCharacter", ".");
    }

    /** Returns all labels of items with the given kind. */
    private static Set<String> labelsOfKind(final List<DynamicCompletionItem> items, final String kind) {
        return items.stream()
            .filter(i -> kind.equals(i.kind()))
            .map(DynamicCompletionItem::label)
            .collect(Collectors.toSet());
    }

    /** Returns all filterTexts of items with the given kind. */
    private static Set<String> filterTextsOfKind(final List<DynamicCompletionItem> items, final String kind) {
        return items.stream()
            .filter(i -> kind.equals(i.kind()))
            .map(DynamicCompletionItem::filterText)
            .collect(Collectors.toSet());
    }

    /** Returns the detail of the first item with the given label, or null. */
    private static String detailOf(final List<DynamicCompletionItem> items, final String label) {
        return items.stream()
            .filter(i -> label.equals(i.label()))
            .map(DynamicCompletionItem::detail)
            .findFirst()
            .orElse(null);
    }

    // ── P0 tests ────────────────────────────────────────────────────────────

    /**
     * P0: ROWID, ROWINDEX and ROWCOUNT items are always present with kind=Field and the correct type details.
     */
    @Test
    public void testP0RowMetadataPresent() {
        var result = m_service.getCompletions(invokedRequest("", 1, 1));

        var fieldLabels = labelsOfKind(result, "Field");
        assertTrue("Expected ROWID field", fieldLabels.contains("ROWID"));
        assertTrue("Expected ROWINDEX field", fieldLabels.contains("ROWINDEX"));
        assertTrue("Expected ROWCOUNT field", fieldLabels.contains("ROWCOUNT"));

        assertEquals("ROWID detail should be String", "String", detailOf(result, "ROWID"));
        assertEquals("ROWINDEX detail should be int", "int", detailOf(result, "ROWINDEX"));
        assertEquals("ROWCOUNT detail should be int", "int", detailOf(result, "ROWCOUNT"));
    }

    /**
     * P0: common Java keywords are always present with kind=Keyword.
     */
    @Test
    public void testP0JavaKeywordsPresent() {
        var result = m_service.getCompletions(invokedRequest("", 1, 1));

        var keywordLabels = labelsOfKind(result, "Keyword");
        for (var kw : List.of("if", "for", "while", "return", "new")) {
            assertTrue("Expected keyword: " + kw, keywordLabels.contains(kw));
        }
    }

    /**
     * P0: AbstractJSnippet helper methods are always present with kind=Method.
     */
    @Test
    public void testP0AbstractJSnippetMethodsPresent() {
        var result = m_service.getCompletions(invokedRequest("", 1, 1));

        var methodFilterTexts = filterTextsOfKind(result, "Method");
        for (var method : List.of("getCell", "isMissing", "getColumnCount", "logWarn")) {
            assertTrue("Expected snippet method: " + method, methodFilterTexts.contains(method));
        }
    }

    /**
     * P0: Configured input columns appear as Field items using their Java field name and include documentation.
     */
    @Test
    public void testP0InputColumnsFromFieldMappings() {
        var inputCols = new JavaSnippetScriptingService.FieldMapping[]{
            new JavaSnippetScriptingService.FieldMapping("Age", "c_Age", "Integer"),
            new JavaSnippetScriptingService.FieldMapping("Name", "c_Name", "String"),
        };
        var service = new JavaSnippetCompletionService(m_workflowControl,
            new JavaSnippetScriptingService.FieldMappings(
                inputCols,
                new JavaSnippetScriptingService.FieldMapping[0],
                new JavaSnippetScriptingService.FieldMapping[0],
                new JavaSnippetScriptingService.FieldMapping[0]));

        var result = service.getCompletions(invokedRequest("", 1, 1));

        var fieldLabels = labelsOfKind(result, "Field");
        assertTrue("Expected c_Age field", fieldLabels.contains("c_Age"));
        assertTrue("Expected c_Name field", fieldLabels.contains("c_Name"));

        var ageItem = result.stream().filter(i -> "c_Age".equals(i.label())).findFirst().orElseThrow();
        assertTrue("Documentation should mention KNIME column name", ageItem.documentation().contains("Age"));
        assertTrue("Documentation should mention Java type", ageItem.documentation().contains("Integer"));
        assertEquals("detail should be the Java type", "Integer", ageItem.detail());
    }

    /**
     * P0: Configured input flow variables appear as Field items using their Java field name.
     */
    @Test
    public void testP0FlowVariablesFromFieldMappings() {
        var inputFvs = new JavaSnippetScriptingService.FieldMapping[]{
            new JavaSnippetScriptingService.FieldMapping("myVar", "fv_myVar", "String"),
        };
        var service = new JavaSnippetCompletionService(m_workflowControl,
            new JavaSnippetScriptingService.FieldMappings(
                new JavaSnippetScriptingService.FieldMapping[0],
                inputFvs,
                new JavaSnippetScriptingService.FieldMapping[0],
                new JavaSnippetScriptingService.FieldMapping[0]));

        var result = service.getCompletions(invokedRequest("", 1, 1));

        var fieldLabels = labelsOfKind(result, "Field");
        assertTrue("Expected fv_myVar field", fieldLabels.contains("fv_myVar"));

        var fvItem = result.stream().filter(i -> "fv_myVar".equals(i.label())).findFirst().orElseThrow();
        assertTrue("Documentation should mention KNIME variable name", fvItem.documentation().contains("myVar"));
    }

    /**
     * P0: Configured output columns appear as Field items with documentation indicating they write to KNIME.
     */
    @Test
    public void testP0OutputColumnsFromFieldMappings() {
        var outputCols = new JavaSnippetScriptingService.FieldMapping[]{
            new JavaSnippetScriptingService.FieldMapping("result", "out_result", "String"),
        };
        var service = new JavaSnippetCompletionService(m_workflowControl,
            new JavaSnippetScriptingService.FieldMappings(
                new JavaSnippetScriptingService.FieldMapping[0],
                new JavaSnippetScriptingService.FieldMapping[0],
                outputCols,
                new JavaSnippetScriptingService.FieldMapping[0]));

        var result = service.getCompletions(invokedRequest("", 1, 1));

        var fieldLabels = labelsOfKind(result, "Field");
        assertTrue("Expected out_result field", fieldLabels.contains("out_result"));

        var outItem = result.stream().filter(i -> "out_result".equals(i.label())).findFirst().orElseThrow();
        assertTrue("Documentation should mention writing to KNIME", outItem.documentation().contains("writes"));
        assertTrue("Documentation should mention KNIME column name", outItem.documentation().contains("result"));
    }

    /**
     * P0: Configured output flow variables appear as Field items with documentation indicating they write to KNIME.
     */
    @Test
    public void testP0OutputFlowVariablesFromFieldMappings() {
        var outputFvs = new JavaSnippetScriptingService.FieldMapping[]{
            new JavaSnippetScriptingService.FieldMapping("outVar", "outfv_outVar", "Double"),
        };
        var service = new JavaSnippetCompletionService(m_workflowControl,
            new JavaSnippetScriptingService.FieldMappings(
                new JavaSnippetScriptingService.FieldMapping[0],
                new JavaSnippetScriptingService.FieldMapping[0],
                new JavaSnippetScriptingService.FieldMapping[0],
                outputFvs));

        var result = service.getCompletions(invokedRequest("", 1, 1));

        var fieldLabels = labelsOfKind(result, "Field");
        assertTrue("Expected outfv_outVar field", fieldLabels.contains("outfv_outVar"));

        var outItem = result.stream().filter(i -> "outfv_outVar".equals(i.label())).findFirst().orElseThrow();
        assertTrue("Documentation should mention writing to KNIME", outItem.documentation().contains("writes"));
    }

    /**
     * P0: When no field mappings are configured, no extra Field items (other than row metadata) are present.
     */
    @Test
    public void testP0EmptyMappingsNoColumnOrFlowVarItems() {
        // m_service uses empty mappings by default from setUp()
        var result = m_service.getCompletions(invokedRequest("", 1, 1));

        var nonMetaFieldItems = result.stream()
            .filter(i -> "Field".equals(i.kind())
                && !"ROWID".equals(i.label())
                && !"ROWINDEX".equals(i.label())
                && !"ROWCOUNT".equals(i.label()))
            .count();
        assertEquals("No non-metadata Field items expected with empty mappings", 0L, nonMetaFieldItems);

        assertTrue("ROWID should be present", labelsOfKind(result, "Field").contains("ROWID"));
        assertTrue("'if' keyword should be present", labelsOfKind(result, "Keyword").contains("if"));
    }

    // ── P1 tests ────────────────────────────────────────────────────────────

    /**
     * P1: "this." triggers reflection on AbstractJSnippet; result contains its public fields/methods and NO keywords
     * (since P0 is never called when P1 succeeds).
     */
    @Test
    public void testP1DotTriggerWithThis() {
        // "    this." is 9 characters → cursor at column 10 (1-based, right after the dot)
        var text = "public class JSnippet extends AbstractJSnippet {\n"
            + "  public void snippet() {\n"
            + "    this.\n"  // line 3
            + "  }\n"
            + "}";
        var result = m_service.getCompletions(dotTriggerRequest(text, 3, 10));

        // P1 was used: no keyword items in the result
        assertFalse("P1 result must not contain Keyword items",
            result.stream().anyMatch(i -> "Keyword".equals(i.kind())));

        // AbstractJSnippet has public fields ROWID, ROWINDEX, ROWCOUNT
        var fieldLabels = labelsOfKind(result, "Field");
        assertTrue("Expected ROWID field from AbstractJSnippet", fieldLabels.contains("ROWID"));
        assertTrue("Expected ROWINDEX field from AbstractJSnippet", fieldLabels.contains("ROWINDEX"));
        assertTrue("Expected ROWCOUNT field from AbstractJSnippet", fieldLabels.contains("ROWCOUNT"));

        // AbstractJSnippet has the public method attachLogger
        var methodFilterTexts = filterTextsOfKind(result, "Method");
        assertTrue("Expected 'attachLogger' method from AbstractJSnippet",
            methodFilterTexts.contains("attachLogger"));
    }

    /**
     * P1: A variable declared as String is resolved to java.lang.String; result contains String instance methods.
     */
    @Test
    public void testP1DotTriggerWithString() {
        // "    x." is 6 characters → cursor at column 7 (1-based, right after the dot)
        var text = "public class JSnippet extends AbstractJSnippet {\n"
            + "  public void snippet() {\n"
            + "    String x = \"hello\";\n"
            + "    x.\n"  // line 4
            + "  }\n"
            + "}";
        var result = m_service.getCompletions(dotTriggerRequest(text, 4, 7));

        // P1 was used: no keyword items
        assertFalse("P1 result must not contain Keyword items",
            result.stream().anyMatch(i -> "Keyword".equals(i.kind())));

        // String instance methods must be present
        var methodFilterTexts = filterTextsOfKind(result, "Method");
        assertTrue("Expected 'length' from String", methodFilterTexts.contains("length"));
        assertTrue("Expected 'charAt' from String", methodFilterTexts.contains("charAt"));
        assertTrue("Expected 'substring' from String", methodFilterTexts.contains("substring"));
    }

    /**
     * P1: An unknown variable triggers fallback to P0; keywords are present in the result.
     */
    @Test
    public void testP1DotTriggerWithUnknownVar() {
        // "  unknownThing." is 15 characters → cursor at column 16
        var text = "  unknownThing.\n";
        var result = m_service.getCompletions(dotTriggerRequest(text, 1, 16));

        // Fell back to P0: keywords must be present
        assertTrue("P0 fallback should contain 'if' keyword",
            labelsOfKind(result, "Keyword").contains("if"));
        assertTrue("P0 fallback should contain ROWID field",
            labelsOfKind(result, "Field").contains("ROWID"));
    }

    /**
     * P1: Manual completion (Invoked) with the cursor right after a dot is still treated as a dot-triggered P1
     * request.
     */
    @Test
    public void testP1ManualInvocationAfterDot() {
        // Same text and position as testP1DotTriggerWithThis but with Invoked trigger
        // The service checks lines[lineIdx].charAt(cursorColumn-2) == '.' to detect this case.
        // "    this." (9 chars) → colIdx = 10-2 = 8 → charAt(8) == '.'
        var text = "public class JSnippet extends AbstractJSnippet {\n"
            + "  public void snippet() {\n"
            + "    this.\n"  // line 3
            + "  }\n"
            + "}";
        var result = m_service.getCompletions(invokedRequest(text, 3, 10));

        // P1 was triggered: no keyword items
        assertFalse("P1 result must not contain Keyword items",
            result.stream().anyMatch(i -> "Keyword".equals(i.kind())));

        // AbstractJSnippet public fields present
        assertTrue("Expected ROWID from AbstractJSnippet reflection",
            labelsOfKind(result, "Field").contains("ROWID"));
    }

    /**
     * P1: An imported class name before a dot is resolved via the import statement. When the class has no public
     * static members (like ArrayList) P1 returns empty and the service falls back to P0.
     */
    @Test
    public void testP1ImportedClass() {
        // "    ArrayList." is 14 characters → cursor at column 15
        var text = "import java.util.ArrayList;\n"
            + "public class JSnippet extends AbstractJSnippet {\n"
            + "  public void snippet() {\n"
            + "    ArrayList.\n"  // line 4
            + "  }\n"
            + "}";
        // ArrayList has no public static methods or fields → P1 returns empty → P0 fallback
        var result = m_service.getCompletions(dotTriggerRequest(text, 4, 15));

        // P0 fallback: keywords are present
        assertTrue("P0 fallback should contain 'while' keyword",
            labelsOfKind(result, "Keyword").contains("while"));
        assertFalse("Result must not be empty", result.isEmpty());
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    /**
     * Passing null as the text must not throw; the service returns P0 completions.
     */
    @Test
    public void testNullText() {
        var result = m_service.getCompletions(new DynamicCompletionRequest(null, 1, 1, "Invoked", null));

        assertFalse("Result must not be empty even for null text", result.isEmpty());
        assertTrue("ROWID should be present in P0 fallback",
            labelsOfKind(result, "Field").contains("ROWID"));
        assertTrue("Keywords should be present in P0 fallback",
            labelsOfKind(result, "Keyword").contains("if"));
    }

    /**
     * An empty text string is handled gracefully; P0 completions are returned.
     */
    @Test
    public void testEmptyText() {
        var result = m_service.getCompletions(invokedRequest("", 1, 1));

        assertFalse("Result must not be empty for empty text", result.isEmpty());
        assertTrue("ROWID should be present in P0 fallback",
            labelsOfKind(result, "Field").contains("ROWID"));
        assertTrue("Keywords should be present in P0 fallback",
            labelsOfKind(result, "Keyword").contains("return"));
    }
}
