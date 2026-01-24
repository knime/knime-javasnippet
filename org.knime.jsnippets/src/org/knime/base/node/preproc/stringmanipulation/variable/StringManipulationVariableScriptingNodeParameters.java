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
 *   22.12.2025 (Ali Asghar Marvi): created
 */
package org.knime.base.node.preproc.stringmanipulation.variable;

import java.util.List;
import java.util.function.Supplier;

import org.knime.base.node.preproc.stringmanipulation.StringManipulationSettings;
import org.knime.base.node.preproc.stringmanipulation.StringManipulationWebUIUtils;
import org.knime.base.node.util.WebUIDialogUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.VariableType.DoubleType;
import org.knime.core.node.workflow.VariableType.IntType;
import org.knime.core.node.workflow.VariableType.StringType;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.legacy.AutoGuessValueProvider;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.FlowVariableChoicesProvider;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.node.parameters.widget.text.TextInputWidget;

/**
 * @author Ali Asghar Marvi, KNIME GmbH, Berlin, Germany
 * @since 5.10
 */

class StringManipulationVariableScriptingNodeParameters implements NodeParameters {

    String m_expression = "";

    @TextMessage(WebUIDialogUtils.FunctionAutoCompletionShortcutInfoMessageProvider.class)
    Void m_textMessage;

    @Persist(configKey = StringManipulationSettings.CFG_TEST_COMPILATION)
    boolean m_syntaxCheckOnClose = true;

    @Widget(title = "Output variable",
        description = "Choose whether to replace an existing or append a new flow variable.")
    @ValueSwitchWidget
    @ValueReference(ReplaceOrAppendRef.class)
    @Persistor(ReplaceOrAppendPersistor.class)
    ReplaceOrAppend m_replaceOrAppend = ReplaceOrAppend.APPEND;

    class ReplaceOrAppendRef implements ParameterReference<ReplaceOrAppend> {
    }

    static final class IsReplace implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(ReplaceOrAppendRef.class).isOneOf(ReplaceOrAppend.REPLACE);
        }
    }

    static final class ReplaceOrAppendProvider implements StateProvider<ReplaceOrAppend> {
        private Supplier<ReplaceOrAppend> m_valueSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeOnValueChange(ReplaceOrAppendRef.class); // we need this during saving time
            m_valueSupplier = initializer.getValueSupplier(ReplaceOrAppendRef.class);
        }

        @SuppressWarnings("restriction")
        @Override
        public ReplaceOrAppend computeState(final NodeParametersInput parametersInput)
            throws StateComputationFailureException {
            return m_valueSupplier.get();
        }

    }

    static final class FlowVariableNameRef implements ParameterReference<String> {
    }

    // auto guess provider for auto-selecting the first flow variable name of type String, Double, or Int
    static final class FlowVariableNameProvider extends AutoGuessValueProvider<String> {

        protected FlowVariableNameProvider() {
            super(FlowVariableNameRef.class);
        }

        @Override
        protected boolean isEmpty(final String value) {
            return value == null || value.isEmpty();
        }

        @Override
        protected String autoGuessValue(final NodeParametersInput parametersInput)
            throws StateComputationFailureException {
            return parametersInput
                .getAvailableInputFlowVariables(StringType.INSTANCE, DoubleType.INSTANCE, IntType.INSTANCE).values()
                .stream().map(FlowVariable::getName).findFirst().orElse(null);
        }

    }

    // a more sensible approach to use as the choices provider for flow variables
    // since the node only accepts String, Double, and Int types.
    static final class FlowVariableNameChoicesProvider implements FlowVariableChoicesProvider {
        @Override
        public List<FlowVariable> flowVariableChoices(final NodeParametersInput input) {
            return input.getAvailableInputFlowVariables(StringType.INSTANCE, DoubleType.INSTANCE, IntType.INSTANCE)
                .values().stream().toList();
        }
    }

    static class OutputVariableName implements NodeParameters {
        @ValueProvider(ReplaceOrAppendProvider.class)
        public ReplaceOrAppend m_replaceOrAppend;

        @Widget(title = "New variable name", description = "The name of the new flow variable to append.")
        @Effect(predicate = IsReplace.class, type = EffectType.HIDE)
        @TextInputWidget
        public String m_variableNameAppend = "new variable";

        @Widget(title = "Replace variable", description = "The name of the flow variable to replace.")
        @ChoicesProvider(FlowVariableNameChoicesProvider.class)
        @Effect(predicate = IsReplace.class, type = EffectType.SHOW)
        @ValueReference(FlowVariableNameRef.class)
        @ValueProvider(value = FlowVariableNameProvider.class)
        public String m_variableNameReplace;
    }

    @Persistor(OutputVariableNamePersistor.class)
    OutputVariableName m_outputVariable = new OutputVariableName();

    enum ReplaceOrAppend {
            @Label(value = "Append", description = "Append a new flow variable")
            APPEND, //
            @Label(value = "Replace", description = "Replace an existing flow variable")
            REPLACE;
    }

    static class ReplaceOrAppendPersistor implements NodeParametersPersistor<ReplaceOrAppend> {

        @Override
        public ReplaceOrAppend load(final NodeSettingsRO settings) throws InvalidSettingsException {
            boolean isReplace = settings.getBoolean(StringManipulationSettings.CFG_IS_REPLACE);
            return isReplace ? ReplaceOrAppend.REPLACE : ReplaceOrAppend.APPEND;
        }

        @Override
        public void save(final ReplaceOrAppend param, final NodeSettingsWO settings) {
            settings.addBoolean(StringManipulationSettings.CFG_IS_REPLACE, param == ReplaceOrAppend.REPLACE);
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{StringManipulationSettings.CFG_IS_REPLACE}};
        }
    }

    static class OutputVariableNamePersistor implements NodeParametersPersistor<OutputVariableName> {

        @Override
        public OutputVariableName load(final NodeSettingsRO settings) throws InvalidSettingsException {
            var output = new OutputVariableName();
            boolean isReplace = settings.getBoolean(StringManipulationSettings.CFG_IS_REPLACE);
            output.m_replaceOrAppend = isReplace ? ReplaceOrAppend.REPLACE : ReplaceOrAppend.APPEND;

            String variableName = settings.getString(StringManipulationSettings.CFG_COLUMN_NAME);
            if (isReplace) {
                output.m_variableNameReplace = variableName;
            } else {
                output.m_variableNameAppend =
                    variableName == null || variableName.isEmpty() ? "new variable" : variableName;
            }

            return output;
        }

        @Override
        public void save(final OutputVariableName param, final NodeSettingsWO settings) {
            boolean isReplace = param.m_replaceOrAppend == ReplaceOrAppend.REPLACE;

            // Only save the variable name if APPEND, otherwise save the replace variable name
            String variableName = isReplace ? param.m_variableNameReplace : param.m_variableNameAppend;
            settings.addString(StringManipulationSettings.CFG_COLUMN_NAME, variableName);
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{StringManipulationSettings.CFG_COLUMN_NAME}};
        }
    }

    @Persistor(StringManipulationWebUIUtils.ReturnTypePersistor.class)
    Class<?> m_returnType;


}