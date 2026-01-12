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

import org.knime.base.node.preproc.stringmanipulation.StringManipulationSettings;
import org.knime.base.node.preproc.stringmanipulation.StringManipulatorProvider;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.VariableType;
import org.knime.core.webui.node.dialog.scripting.AbstractDefaultScriptingNodeDialog.StaticCompletionItem;
import org.knime.core.webui.node.dialog.scripting.InputOutputModel;
import org.knime.core.webui.node.dialog.scripting.WorkflowControl;
import org.knime.node.parameters.persistence.NodeParametersPersistor;

/**
 * @author Ali Asghar Marvi, KNIME GmbH, Berlin, Germany
 * @since 5.10
 */
@SuppressWarnings("restriction")
public final class WebUIDialogUtils {

    private WebUIDialogUtils() {
    }

    public static final Set<VariableType<?>> SUPPORTED_VARIABLE_TYPES =
        Set.of(VariableType.StringType.INSTANCE, VariableType.IntType.INSTANCE, VariableType.DoubleType.INSTANCE);

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
              $${D {{~{subItems.[0].name}~}} }$$
            {{~/when~}}
            {{~#when subItems.[0].type.id 'eq' 'STRING'~}}
              $${S {{~{subItems.[0].name}~}} }$$
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
     * Get auto completion items for string manipulator nodes.
     *
     * @param workflowControl a utility class to access workflow controls
     * @param includeColumns a boolean flag to add columns names for auto-completion.
     * @return
     */
    public static StaticCompletionItem[] getCompletionItems(final WorkflowControl workflowControl,
        final boolean includeColumns) {
        Set<StaticCompletionItem> items = new HashSet<>();

        // Add string manipulator functions
        StringManipulatorProvider.getDefault().getCategories().stream()
            .forEach(c -> StringManipulatorProvider.getDefault().getManipulators(c).forEach(m -> {
                var displayName = m.getDisplayName();
                var arguments = displayName.substring(displayName.indexOf('(') + 1, displayName.lastIndexOf(')'));
                items.add(new StaticCompletionItem(m.getName(), arguments, m.getDescription(),
                    m.getReturnType().getSimpleName()));
            }));

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

    public static final class ReturnTypePersistor implements NodeParametersPersistor<Class<?>> {

        @Override
        public Class<?> load(final NodeSettingsRO settings) throws InvalidSettingsException {
            String returnType = settings.getString(StringManipulationSettings.CFG_RETURN_TYPE, null);

            if (returnType == null) {
                return null;
            } else {
                return StringManipulationSettings.getClassForReturnType(returnType);
            }

        }

        @Override
        public void save(final Class<?> param, final NodeSettingsWO settings) {
            String returnTypeStr = param != null ? param.getName() : null;
            settings.addString(StringManipulationSettings.CFG_RETURN_TYPE, returnTypeStr);
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{StringManipulationSettings.CFG_RETURN_TYPE}};
        }
    }
}