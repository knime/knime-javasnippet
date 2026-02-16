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
 *   Jan 7, 2026 (Ali Asghar Marvi): created
 */
package org.knime.base.node.util;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.VariableType;
import org.knime.core.webui.node.dialog.scripting.AbstractDefaultScriptingNodeDialog.StaticCompletionItem;
import org.knime.core.webui.node.dialog.scripting.InputOutputModel;
import org.knime.core.webui.node.dialog.scripting.WorkflowControl;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.node.parameters.widget.message.TextMessage.MessageType;

/**
 * @author Ali Asghar Marvi, KNIME GmbH, Berlin, Germany
 * @since 5.10
 */
@SuppressWarnings("restriction")
public final class WebUIDialogUtils {

    private WebUIDialogUtils() {
    }

    /**
     * Supported flow variable data types in scripting nodes.
     */
    public static final Set<VariableType<?>> SUPPORTED_VARIABLE_TYPES =
        Set.of(VariableType.StringType.INSTANCE, VariableType.IntType.INSTANCE, VariableType.DoubleType.INSTANCE);

    /**
     * Default language identifier for scripting editor syntax highlighting.
     */
    public static final String DEFAULT_SCRIPT_LANGUAGE = "plaintext";

    /**
     * Default file name for script files.
     */
    public static final String DEFAULT_SCRIPT_FILE_NAME = "script.txt";

    /**
     * Data supplier key for input objects (tables/columns).
     */
    public static final String DATA_SUPPLIER_KEY_INPUT_OBJECTS = "inputObjects";

    /**
     * Data supplier key for flow variables.
     */
    public static final String DATA_SUPPLIER_KEY_FLOW_VARIABLES = "flowVariables";

    /**
     * Data supplier key for output objects.
     */
    public static final String DATA_SUPPLIER_KEY_OUTPUT_OBJECTS = "outputObjects";

    /**
     * Data supplier key for script language.
     */
    public static final String DATA_SUPPLIER_KEY_LANGUAGE = "language";

    /**
     * Data supplier key for script file name.
     */
    public static final String DATA_SUPPLIER_KEY_FILE_NAME = "fileName";

    /**
     * Data supplier key for main script configuration key.
     */
    public static final String DATA_SUPPLIER_KEY_MAIN_SCRIPT_CONFIG_KEY = "mainScriptConfigKey";

    /**
     * Data supplier key for static completion items.
     */
    public static final String DATA_SUPPLIER_KEY_STATIC_COMPLETION_ITEMS = "staticCompletionItems";

    /**
     * Column aliasing template for column names when they are inserted or dragged from the side panel onto the
     * scripting editor.
     */
    public static final String COLUMN_ALIAS_TEMPLATE = """
            {{~#if subItems.[0].insertionText~}}
                {{ subItems.[0].insertionText }}
            {{~else~}}
                ${{~{ subItems.[0].name }~}}$
            {{~/if~}}
            """;

    /**
     * Flow variable aliasing template for flow variables when they are inserted or dragged from the side panel onto the
     * scripting editor.
     */
    public static final String FLOWVAR_ALIAS_TEMPLATE = """
            {{#when subItems.[0].type.id 'eq' 'INTEGER'~}}
              $${I {{~{ subItems.[0].name }~}} }$$
            {{~/when~}}
            {{~#when subItems.[0].type.id 'eq' 'DOUBLE'~}}
              $${D {{~{ subItems.[0].name }~}} }$$
            {{~/when~}}
            {{~#when subItems.[0].type.id 'eq' 'STRING'~}}
              $${S {{~{ subItems.[0].name }~}} }$$
            {{~/when}}""";

    /**
     * This function returns the type prefix character for a given flow variable data type.
     *
     * @param variable the flow variable holding the variable
     * @return the type prefix character
     */
    public static char getFlowVariableTypePrefix(final FlowVariable variable) {
        if (variable.getVariableType().getSimpleType() == Double.class) {
            return 'D';
        } else if (variable.getVariableType().getSimpleType() == String.class) {
            return 'S';
        } else if (variable.getVariableType().getSimpleType() == Integer.class) {
            return 'I';
        }
        throw new IllegalArgumentException(
            "Flow Variable is of unsupported type: " + variable.getVariableType().getIdentifier());
    }

    /**
     * Creates an {@link InputOutputModel} for flow variables supported by string manipulation.
     *
     * @param workflowControl the workflow control to get flow variables from
     * @return the flow variables input/output model
     */
    public static InputOutputModel getFlowVariablesInputOutputModel(final WorkflowControl workflowControl) {
        var flowVariables = Optional.ofNullable(workflowControl.getFlowObjectStack()) //
            .map(stack -> stack.getAllAvailableFlowVariables().values()) //
            .orElseGet(List::of) //
            .stream() //
            .filter(fv -> SUPPORTED_VARIABLE_TYPES.contains(fv.getVariableType())) //
            .toList();

        return InputOutputModel.flowVariables() //
            .subItems(flowVariables, SUPPORTED_VARIABLE_TYPES::contains) //
            .subItemCodeAliasTemplate(FLOWVAR_ALIAS_TEMPLATE) //
            .build();
    }

    /**
     * Creates a list of {@link InputOutputModel} for input table columns.
     *
     * @param workflowControl the workflow control to get input specs of Table
     * @return a list containing the input table model, or empty list if no table input
     */
    public static List<InputOutputModel> getFirstInputTableModel(final WorkflowControl workflowControl) {
        var inputSpecs = Optional.ofNullable(workflowControl.getInputSpec()) //
            .orElse(new PortObjectSpec[0]);

        if (inputSpecs.length > 0 && inputSpecs[0] instanceof DataTableSpec tableSpec) {
            var inputModel = InputOutputModel.table() //
                .name("Input Table") //
                .subItemCodeAliasTemplate(COLUMN_ALIAS_TEMPLATE) //
                .subItems(tableSpec, dataType -> dataType.getName()) //
                .build();
            return List.of(inputModel);
        }

        return List.of();
    }

    /**
     * Extracts arguments from a manipulator's display name. Handles both function-style display names (e.g.,
     * "functionName(arg1, arg2)") and operator-style display names (e.g., "? AND ?", "NOT ?").
     *
     * @param displayName the display name of a manipulator
     * @return the arguments string if the display name follows function pattern, null otherwise
     */
    public static String extractArguments(final String displayName) {
        int openParen = displayName.indexOf('(');
        int closeParen = displayName.lastIndexOf(')');

        // Only extract arguments if display name has parentheses
        if (openParen >= 0 && closeParen > openParen) {
            return displayName.substring(openParen + 1, closeParen);
        }

        // For operators and other non-function style manipulators, return null
        return null;
    }

    /**
     * Get auto completion items for manipulator provider implementations.
     *
     * @param workflowControl a utility class to access workflow controls
     * @param manipulatorProvider a {@link ManipulatorProvider} implementation providing manipulator functions, or null
     *            to skip adding manipulator functions
     * @param includeColumns a boolean flag to add columns names for auto-completion.
     * @return an array of {@link StaticCompletionItem} objects containing manipulator functions, flow variables, and
     *         optionally column names
     */
    public static StaticCompletionItem[] getCompletionItems(final WorkflowControl workflowControl,
        final ManipulatorProvider manipulatorProvider, final boolean includeColumns) {
        Set<StaticCompletionItem> items = new HashSet<>();

        // Add manipulator functions from the provided provider if not null
        if (manipulatorProvider != null) {
            manipulatorProvider.getCategories().stream()
                .forEach(c -> manipulatorProvider.getManipulators(c).forEach(m -> {
                    var displayName = m.getDisplayName();
                    var arguments = extractArguments(displayName);
                    items.add(new StaticCompletionItem(m.getName(), arguments, m.getDescription(),
                        m.getReturnType().getSimpleName()));
                }));
        }

        // Add flow variables
        Optional.ofNullable(workflowControl.getFlowObjectStack()) //
            .map(stack -> stack.getAllAvailableFlowVariables().values()) //
            .orElseGet(List::of) //
            .forEach(fv -> {
                if (SUPPORTED_VARIABLE_TYPES.contains(fv.getVariableType())) {
                    items.add(new StaticCompletionItem(//
                        "$${" + getFlowVariableTypePrefix(fv) + fv.getName() + "}$$", //
                        null, //
                        "Input flow variable '" + fv.getName() + "'", //
                        fv.getVariableType().getSimpleType().getSimpleName()));
                }
            });

        // Add columns if requested
        if (includeColumns) {
            var inputSpecs = Optional.ofNullable(workflowControl.getInputSpec()) //
                .orElse(new PortObjectSpec[0]);

            if (inputSpecs.length > 0 && inputSpecs[0] instanceof DataTableSpec tableSpec) {
                tableSpec.forEach(dcs -> items.add( //
                    new StaticCompletionItem( //
                        "$" + dcs.getName() + "$", //
                        null, //
                        "Input column '" + dcs.getName() + "'", //
                        dcs.getType().getName())));
            }
        }

        return items.toArray(StaticCompletionItem[]::new);
    }

    private static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");

    private static final String MAC_SHORTCUT = "Cmd+I";

    private static final String WIN_LINUX_SHORTCUT = "Ctrl+Space";

    /**
     * Abstract base class for providing info messages about keyboard shortcuts in scripting editors. Subclasses should
     * specify the terminology to use (e.g., "rules" vs "functions").
     */
    abstract static class AbstractAutoCompleteShortcutInfoMessageProvider
        implements TextMessage.SimpleTextMessageProvider {

        /**
         * Returns the term to use for each text info provider (e.g., "rules", "functions").
         *
         * @return the terminology string
         */
        protected abstract String getTerminology();

        @Override
        public boolean showMessage(final NodeParametersInput context) {
            return true;
        }

        @Override
        public String title() {
            return "Access to Available " + getTerminology().toLowerCase();
        }

        @Override
        public String description() {
            String shortcut = IS_MAC ? MAC_SHORTCUT : WIN_LINUX_SHORTCUT;

            return "While working in the code editor, press " + shortcut + " to see a list of all available "
                + getTerminology() + ". " + "Press the shortcut again to show the description of each item. "
                + "Press Enter to insert the selected item.";
        }

        @Override
        public MessageType type() {
            return MessageType.INFO;
        }

    }

    /**
     * Text info message provider for Rule Engine nodes.
     */
    public static final class RuleEngineEditorAutoCompletionShortcutInfoMessageProvider
        extends AbstractAutoCompleteShortcutInfoMessageProvider {
        @Override
        protected String getTerminology() {
            return "rules";
        }
    }

    /**
     * Text info message provider for String Manipulation nodes.
     */
    public static final class FunctionAutoCompletionShortcutInfoMessageProvider
        extends AbstractAutoCompleteShortcutInfoMessageProvider {
        @Override
        protected String getTerminology() {
            return "functions";
        }
    }

}