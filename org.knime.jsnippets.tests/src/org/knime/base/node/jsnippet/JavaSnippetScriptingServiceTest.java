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
 *   Mar 19, 2026 (chaubold): created
 */
package org.knime.base.node.jsnippet;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.webui.node.dialog.scripting.CodeGenerationRequest;
import org.knime.core.webui.node.dialog.scripting.CodeGenerationRequest.PromptRequestBody;
import org.knime.core.webui.node.dialog.scripting.InputOutputModel;
import org.knime.core.webui.node.dialog.scripting.WorkflowControl;

/**
 * Tests for the prompt content produced by
 * {@link JavaSnippetScriptingService.JavaSnippetRpcService#getCodeSuggestionRequest}.
 *
 * @author Carsten Haubold, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
public class JavaSnippetScriptingServiceTest {

    private WorkflowControl m_workflowControl;

    private JavaSnippetScriptingService.JavaSnippetRpcService m_rpcService;

    @Before
    public void setUp() {
        m_workflowControl = mock(WorkflowControl.class);
        when(m_workflowControl.getInputSpec()).thenReturn(new PortObjectSpec[0]);
        var service = new JavaSnippetScriptingService(m_workflowControl);
        m_rpcService = (JavaSnippetScriptingService.JavaSnippetRpcService) service.getJsonRpcService();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Builds an {@link InputOutputModel} array with one table port containing the given spec and no flow variables.
     */
    private static InputOutputModel[] tableModels(final DataTableSpec spec) {
        return new InputOutputModel[]{
            InputOutputModel.table().name("Input table 1").subItems(spec, dt -> dt.getName()).build()};
    }

    /**
     * Builds an {@link InputOutputModel} array with no table and a flow-variable port containing the given variables.
     * Uses {@link Object#toString()} on the {@link org.knime.core.node.workflow.VariableType} to produce the display
     * name (e.g. "STRING"), matching the format expected by
     * {@link JavaSnippetScriptingService.JavaSnippetRpcService}'s type-prefix mapper.
     */
    private static InputOutputModel[] flowVarModels(final List<FlowVariable> flowVariables) {
        return new InputOutputModel[]{InputOutputModel.flowVariables()
            .subItems(flowVariables, vt -> vt.toString(), vt -> true).build()};
    }

    /**
     * Extracts the {@code prompt} string from a {@link PromptRequestBody} via reflection (the field is private).
     */
    private static String extractPrompt(final CodeGenerationRequest request) throws Exception {
        var body = (PromptRequestBody) request.body();
        Field promptField = PromptRequestBody.class.getDeclaredField("prompt");
        promptField.setAccessible(true);
        return (String) promptField.get(body);
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    /**
     * The prompt must contain the read-only Java Snippet class and method scaffolding that serves as the anchor for the
     * AI to place generated code.
     */
    @Test
    public void testPromptContainsTemplateAnchors() throws Exception {
        var request = m_rpcService.getCodeSuggestionRequest("my prompt", "", new InputOutputModel[0]);
        var prompt = extractPrompt(request);

        assertTrue("Prompt must contain class declaration anchor",
            prompt.contains("public class JSnippet extends AbstractJSnippet {"));
        assertTrue("Prompt must contain method signature anchor",
            prompt.contains("public void snippet() throws TypeException, ColumnException, Abort {"));
        assertTrue("Prompt must contain closing brace anchor", prompt.contains("  }\n}"));
    }

    /**
     * When the input models contain a table, column names and types must appear in the prompt.
     */
    @Test
    public void testPromptContainsInputColumns() throws Exception {
        var spec = new DataTableSpec(
            new DataColumnSpecCreator("myColumn", StringCell.TYPE).createSpec(),
            new DataColumnSpecCreator("otherColumn", IntCell.TYPE).createSpec());

        var request = m_rpcService.getCodeSuggestionRequest("my prompt", "", tableModels(spec));
        var prompt = extractPrompt(request);

        assertTrue("Prompt must contain first column name", prompt.contains("myColumn"));
        assertTrue("Prompt must contain second column name", prompt.contains("otherColumn"));
    }

    /**
     * When the input models contain flow variables, their names must appear in the prompt with the correct
     * {@code $${Sname}$$} syntax.
     */
    @Test
    public void testPromptContainsFlowVariables() throws Exception {
        var flowVar = new FlowVariable("myVar", "someValue");

        var request = m_rpcService.getCodeSuggestionRequest("my prompt", "", flowVarModels(List.of(flowVar)));
        var prompt = extractPrompt(request);

        assertTrue("Prompt must contain flow variable name", prompt.contains("myVar"));
        // typeId "STRING" from StringType.toString() maps to prefix "S"
        assertTrue("Prompt must contain flow variable placeholder syntax", prompt.contains("$${SmyVar}$$"));
    }

    /**
     * The current code that the user has in the editor must appear verbatim in the prompt.
     */
    @Test
    public void testPromptContainsCurrentCode() throws Exception {
        var currentCode = "out_column = in_column + 1; // existing code";

        var request = m_rpcService.getCodeSuggestionRequest("my prompt", currentCode, new InputOutputModel[0]);
        var prompt = extractPrompt(request);

        assertTrue("Prompt must contain the current code", prompt.contains(currentCode));
    }

    /**
     * The user's natural-language request must appear verbatim in the prompt.
     */
    @Test
    public void testPromptContainsUserPrompt() throws Exception {
        var userPrompt = "please multiply column A by 2";

        var request = m_rpcService.getCodeSuggestionRequest(userPrompt, "", new InputOutputModel[0]);
        var prompt = extractPrompt(request);

        assertTrue("Prompt must contain the user prompt", prompt.contains(userPrompt));
    }
}
