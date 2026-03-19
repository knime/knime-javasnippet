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
 *   Created on Feb 16, 2026 by GitHub Copilot
 */
package org.knime.base.node.jsnippet;

import java.util.List;

import org.knime.core.node.Node;
import org.knime.core.node.workflow.VariableType.DoubleType;
import org.knime.core.node.workflow.VariableType.IntType;
import org.knime.core.node.workflow.VariableType.StringType;
import org.knime.core.webui.node.dialog.scripting.CodeGenerationRequest;
import org.knime.core.webui.node.dialog.scripting.CodeGenerationRequest.PromptRequestBody;
import org.knime.core.webui.node.dialog.scripting.DynamicCompletionItem;
import org.knime.core.webui.node.dialog.scripting.DynamicCompletionRequest;
import org.knime.core.webui.node.dialog.scripting.InputOutputModel;
import org.knime.core.webui.node.dialog.scripting.InputOutputModelNameAndTypeUtils;
import org.knime.core.webui.node.dialog.scripting.ScriptingService;
import org.knime.core.webui.node.dialog.scripting.WorkflowControl;

/**
 * Custom {@link ScriptingService} for the Java Snippet node that delegates {@code getCompletions} to
 * {@link JavaSnippetCompletionService}.
 *
 * @author GitHub Copilot
 * @author Carsten Haubold, KNIME GmbH, Konstanz, Germany
 * @since 5.11
 */
@SuppressWarnings("restriction")
public class JavaSnippetScriptingService extends ScriptingService {

    /**
     * Creates a new Java Snippet scripting service.
     *
     * @param workflowControl the workflow control for the node
     */
    public JavaSnippetScriptingService(final WorkflowControl workflowControl) {
        super(null,
            t -> t == StringType.INSTANCE || t == IntType.INSTANCE || t == DoubleType.INSTANCE,
            workflowControl);
    }

    @Override
    public RpcService getJsonRpcService() {
        return new JavaSnippetRpcService();
    }

    /**
     * The JSON-RPC service implementation for the Java Snippet node.
     */
    public class JavaSnippetRpcService extends RpcService {

        @Override
        protected CodeGenerationRequest getCodeSuggestionRequest(final String userPrompt,
                final String currentCode, final InputOutputModel[] inputModels) {
            var nodeDescription = Node.invokeGetNodeDescription(new JavaSnippetNodeFactory());
            var nodeName = nodeDescription.getNodeName();

            var inputTables = InputOutputModelNameAndTypeUtils.getAllTables(inputModels);
            var flowVariables = InputOutputModelNameAndTypeUtils.getSupportedFlowVariables(inputModels);

            // Build a description of available input columns and flow variables
            var contextInfo = new StringBuilder();

            if (inputTables.length > 0 && inputTables[0].length > 0) {
                contextInfo.append("INPUT TABLE COLUMNS:\n");
                contextInfo.append("Access columns as fields using $columnName$ syntax:\n");
                for (var column : inputTables[0]) {
                    contextInfo.append("- ").append(column.name())
                        .append(" (").append(column.type()).append(")")
                        .append(" -> use as $").append(column.name()).append("$\n");
                }
                contextInfo.append("\n");
            }

            if (flowVariables.length > 0) {
                contextInfo.append("FLOW VARIABLES:\n");
                contextInfo.append("Access flow variables using $${SvariableName}$$ syntax (S=String, I=Integer, D=Double):\n");
                for (var flowVar : flowVariables) {
                    var typePrefix = getFlowVariableTypePrefix(flowVar.type());
                    contextInfo.append("- ").append(flowVar.name())
                        .append(" (").append(flowVar.type()).append(")")
                        .append(" -> use as $${")
                        .append(typePrefix).append(flowVar.name()).append("}$$\n");
                }
                contextInfo.append("\n");
            }

            String systemPrompt = """
                You are a code assistant for KNIME's Java Snippet node. The user writes Java code inside \
                a class that extends AbstractJSnippet.

                The code is structured in three sections:
                1. Import statements (at the top)
                2. Field declarations (inside the class body, before the method)
                3. Method body (inside `public void snippet() throws TypeException, ColumnException, Abort`)

                Available API from AbstractJSnippet:
                - ROWID (String): Current row ID
                - ROWINDEX (int): Current row index (0-based)
                - ROWCOUNT (int): Total number of rows
                - getCell(String column, T type): Get cell value for a column
                - isMissing(String column): Check if a cell value is missing
                - getColumnCount(): Get the number of input columns
                - getColumnName(int index): Get column name by index
                - columnExists(String column): Check if a column exists
                - getFlowVariable(String name, T type): Get a flow variable value
                - flowVariableExists(String name): Check if a flow variable exists
                - logInfo/logWarn/logError/logDebug(Object): Log messages

                Input columns are accessed as fields: $columnName$ (e.g., $Age$ for column "Age")
                Flow variables are accessed as: $${SvariableName}$$ \
                (e.g., $${SfilePath}$$ for String "filePath", $${Icount}$$ for Integer "count", $${Dthreshold}$$ for Double "threshold")
                Output columns are also fields that you write to.

                Default imports: AbstractJSnippet, Abort, Cell, ColumnException, TypeException, Type.*, \
                java.util.Date, java.util.Calendar, org.w3c.dom.Document
                """;

            String prompt = """
                    %s

                    %s

                    Node Purpose:
                    %s

                    CURRENT CODE:
                    %s

                    GUIDELINES:
                    - Return the complete editor content for all three sections (imports, fields, method body)
                    - Preserve existing imports and fields unless changes are required
                    - Use $columnName$ syntax to access input columns
                    - Use $${Sname}$$ syntax to access flow variables \
                    (S=String, I=Integer, D=Double)
                    - Write clean, idiomatic Java that matches the node's intended use case

                    User Request:
                    %s

                    RESPONSE FORMAT:
                    - Return ONLY the Java Snippet code as plain text, structured exactly as shown below
                    - Do NOT wrap it in ```java``` code blocks
                    - Do NOT include explanations outside the code

                    Your response MUST follow this exact template. The class declaration, method signature, \
                    and closing braces are fixed read-only anchors that must appear verbatim:

                    <imports here>

                    public class JSnippet extends AbstractJSnippet {
                    <fields here>

                      @Override
                      public void snippet() throws TypeException, ColumnException, Abort {
                    <body here>
                      }
                    }
                    """.formatted(systemPrompt, contextInfo.toString(),
                    nodeDescription.getXMLDescription(), currentCode, userPrompt);

            return new CodeGenerationRequest("/prompt", new PromptRequestBody(prompt, nodeName));
        }

        private static String getFlowVariableTypePrefix(final String typeId) {
            return switch (typeId.toUpperCase()) {
                case "INTEGER" -> "I";
                case "DOUBLE" -> "D";
                case "STRING" -> "S";
                default -> "S"; // fall back to String for unsupported types
            };
        }

        @Override
        public List<DynamicCompletionItem> getCompletions(final DynamicCompletionRequest request) {
            return new JavaSnippetCompletionService(getWorkflowControl()).getCompletions(request);
        }
    }
}
