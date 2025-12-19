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
 *   Dec 17, 2025 (Marc Lehner): created
 */
package org.knime.base.node.preproc.stringmanipulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.VariableType;
import org.knime.core.webui.node.dialog.scripting.AbstractDefaultScriptingNodeDialog;
import org.knime.core.webui.node.dialog.scripting.GenericInitialDataBuilder;
import org.knime.core.webui.node.dialog.scripting.InputOutputModel;
import org.knime.core.webui.node.dialog.scripting.WorkflowControl;

/**
 *
 * @author Marc Lehner, KNIME AG, Zurich, Switzerland
 * @author Carsten Haubold, KNIME GmbH, Konstanz, Germany
 * @since 5.9
 */
@SuppressWarnings("restriction")
public class StringManipulationScriptingNodeDialog extends AbstractDefaultScriptingNodeDialog {

    /**
     * @param modelSettings
     */
    protected StringManipulationScriptingNodeDialog() {
        super(StringManipulationScriptingNodeSettings.class);
    }

    private static final String COLUMN_ALIAS_TEMPLATE = """
            {{~#if subItems.[0].insertionText~}}
                {{ subItems.[0].insertionText }}
            {{~else~}}
                ${{~{ subItems.[0].name }~}}$
            {{~/if~}}
            """;

    private static final String FLOWVAR_ALIAS_TEMPLATE = """
            {{#when subItems.[0].type.id 'eq' 'INTEGER'~}}
              $${I {{~{ subItems.[0].name }~}} }$$
            {{~/when~}}
            {{~#when subItems.[0].type.id 'eq' 'DOUBLE'~}}
              $${D {{~{subItems.[0].name}~}} }$$
            {{~/when~}}
            {{~#when subItems.[0].type.id 'eq' 'STRING'~}}
              $${S {{~{subItems.[0].name}~}} }$$
            {{~/when}}""";

    private static final Set<VariableType<?>> SUPPORTED_VARIABLE_TYPES = Set.of(
        VariableType.StringType.INSTANCE,
        VariableType.IntType.INSTANCE,
        VariableType.DoubleType.INSTANCE
    );

    /**
     * {@inheritDoc}
     */
    @Override
    protected GenericInitialDataBuilder getInitialData(final NodeContext context) {
        var workflowControl = new WorkflowControl(context.getNodeContainer());
        return GenericInitialDataBuilder.createDefaultInitialDataBuilder(NodeContext.getContext()) //
            .addDataSupplier("inputObjects", () -> {
                var inputSpecs = Optional.ofNullable(workflowControl.getInputSpec()) //
                    .orElse(new PortObjectSpec[0]);

                var inputObjects = new ArrayList<InputOutputModel>();

                // Check if we have at least one input and it's a DataTableSpec
                if (inputSpecs.length > 0 && inputSpecs[0] instanceof DataTableSpec tableSpec) {
                    // Create a single InputOutputModel representing the table with all its columns
                    var inputModel = InputOutputModel.table() //
                        .name("Input Table") //
                        .subItemCodeAliasTemplate(COLUMN_ALIAS_TEMPLATE) //
                        .subItems(tableSpec, dataType -> dataType.getName()) //
                        .build();
                    inputObjects.add(inputModel);
                }

                return inputObjects;
            }) //
            .addDataSupplier("flowVariables", () -> {
                var flowVariables = Optional.ofNullable(workflowControl.getFlowObjectStack()) //
                    .map(stack -> stack.getAllAvailableFlowVariables().values()) //
                    .orElseGet(List::of) //
                    .stream() //
                    .filter(fv -> SUPPORTED_VARIABLE_TYPES.contains(fv.getVariableType())) //
                    .toList();

                return InputOutputModel.flowVariables() //
                    .subItems(flowVariables, varType -> true) //
                    .subItemCodeAliasTemplate(FLOWVAR_ALIAS_TEMPLATE) //
                    .build();
            }) //
            .addDataSupplier("outputObjects", Collections::emptyList) //
            .addDataSupplier("language", () -> "plaintext") //
            .addDataSupplier("fileName", () -> "script.txt") //
            .addDataSupplier("staticCompletionItems", () -> getCompletionItems(workflowControl));
    }

    private static StaticCompletionItem[] getCompletionItems(final WorkflowControl workflowControl) {
        Set<StaticCompletionItem> items = new HashSet<>();
        StringManipulatorProvider.getDefault().getCategories().stream()
            .forEach(c -> StringManipulatorProvider.getDefault().getManipulators(c).forEach(m -> {
                var displayName = m.getDisplayName();
                var arguments = displayName.substring(displayName.indexOf('(') + 1, displayName.lastIndexOf(')'));
                items.add(new StaticCompletionItem(m.getName(), arguments, m.getDescription(), m.getReturnType().getSimpleName()));
            }));

        // Add flow variables
        Optional.ofNullable(workflowControl.getFlowObjectStack()) //
                .map(stack -> stack.getAllAvailableFlowVariables().values()) //
                .orElseGet(List::of) //
                .forEach(fv -> items.add( //
                    new StaticCompletionItem(//
                        "$$" + fv.getName() + "$$", //
                        null, //
                        "Input flow variable '" + fv.getName() + "'", //
                        fv.getVariableType().getSimpleType().getSimpleName())));

        // Add columns
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

        return items.toArray(StaticCompletionItem[]::new);
    }

}
