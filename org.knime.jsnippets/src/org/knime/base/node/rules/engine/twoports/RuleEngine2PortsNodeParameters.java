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
 *   Mar 23, 2026 (Jochen Reißinger, TNG Technology Consulting GmbH): created
 */
package org.knime.base.node.rules.engine.twoports;

import static org.knime.core.webui.node.dialog.defaultdialog.setting.singleselection.NoneChoice.NONE;

import java.util.Optional;

import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.StringValue;
import org.knime.core.webui.node.dialog.defaultdialog.setting.singleselection.NoneChoice;
import org.knime.core.webui.node.dialog.defaultdialog.setting.singleselection.StringOrEnum;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.persistence.legacy.EnumBooleanPersistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.legacy.ColumnNameAutoGuessValueProvider;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.choices.util.AllColumnsProvider;
import org.knime.node.parameters.widget.choices.util.ColumnSelectionUtil;
import org.knime.node.parameters.widget.number.NumberInputWidget;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation.MinValidation.IsNonNegativeValidation;

/**
 * Node parameters for the Rule Engine (Dictionary) node.
 * <p>
 * Extends {@link AbstractRuleEngine2PortsNodeParameters} with the append/replace output column settings and PMML
 * settings.
 *
 * <h3>Backwards compatibility / migration</h3> Old workflows stored the append/replace settings under
 * {@code append.column}, {@code replace.column} and {@code is.replaced.column}. PMML settings are stored under their
 * respective keys.
 *
 * @author Jochen Reißinger, TNG Technology Consulting GmbH
 * @since 5.12
 */
@SuppressWarnings("restriction")
final class RuleEngine2PortsNodeParameters extends AbstractRuleEngine2PortsNodeParameters {

    // -----------------------------------------------------------------------------------------------------------------
    // Output column: append or replace
    // -----------------------------------------------------------------------------------------------------------------

    private interface OutputModeRef extends ParameterReference<OutputMode> {
    }

    private static final class IsReplaceMode implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(OutputModeRef.class).isOneOf(OutputMode.REPLACE);
        }
    }

    private static final class IsAppendMode implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(OutputModeRef.class).isOneOf(OutputMode.APPEND);
        }
    }

    private enum OutputMode {
            @Label(value = "Append column",
                description = "Append a new column with the rule outcome to the input table.")
            APPEND, //
            @Label(value = "Replace column",
                description = "Replace an existing column in the input table with the rule outcome.")
            REPLACE;
    }

    private static final class OutputModePersistor extends EnumBooleanPersistor<OutputMode> {
        OutputModePersistor() {
            super(RuleEngine2PortsSettings.IS_REPLACED_COLUMN, OutputMode.class, OutputMode.REPLACE);
        }
    }

    @Widget(title = "Output mode",
        description = "Choose whether to append a new column with the outcome of the rules, "
            + "or replace an existing column.")
    @ValueSwitchWidget
    @ValueReference(OutputModeRef.class)
    @Persistor(OutputModePersistor.class)
    OutputMode m_outputMode = OutputMode.APPEND;

    @Widget(title = "Append column",
        description = "Name of the newly appended column, which contains the outcome of the rules.")
    @Effect(predicate = IsAppendMode.class, type = EffectType.SHOW)
    @Persist(configKey = RuleEngine2PortsSettings.APPEND_COLUMN)
    String m_appendColumn = RuleEngine2PortsSettings.DEFAULT_APPEND_COLUMN;

    private interface ReplaceColumnRef extends ParameterReference<String> {
    }

    private static final class ReplaceColumnAutoGuesser extends ColumnNameAutoGuessValueProvider {
        ReplaceColumnAutoGuesser() {
            super(ReplaceColumnRef.class);
        }

        @Override
        protected Optional<DataColumnSpec> autoGuessColumn(final NodeParametersInput parametersInput) {
            // The old dialog defaulted to the first String column of the input (data) table
            return ColumnSelectionUtil.getFirstCompatibleColumnOfFirstPort(parametersInput, StringValue.class);
        }
    }

    private static final class DataTableAllColumnsProvider extends AllColumnsProvider {
        @Override
        public int getInputTableIndex(final NodeParametersInput parametersInput) {
            return RuleEngine2PortsNodeModel.DATA_PORT;
        }
    }

    @Widget(title = "Replace column", description = "The column to replace with the rule outcome.")
    @ChoicesProvider(DataTableAllColumnsProvider.class)
    @ValueReference(ReplaceColumnRef.class)
    @ValueProvider(ReplaceColumnAutoGuesser.class)
    @Effect(predicate = IsReplaceMode.class, type = EffectType.SHOW)
    @Persist(configKey = RuleEngine2PortsSettings.REPLACE_COLUMN)
    String m_replaceColumn = "";

    // -----------------------------------------------------------------------------------------------------------------
    // PMML settings
    // -----------------------------------------------------------------------------------------------------------------

    @Widget(title = "Enable PMML RuleSet generation",
        description = "When checked, PMML mode evaluation is used and fails if the input cannot be translated to PMML.")
    @ValueReference(RuleEngine2PortsPmmlSettings.IsPmmlEnabledRef.class)
    @Persist(configKey = RuleEngine2PortsSettings.IS_PMML_RULESET)
    boolean m_isPmmlRuleSet = RuleEngine2PortsSettings.DEFAULT_IS_PMML_RULESET;

    @Widget(title = "Hit selection",
        description = "The method used to select the outcome when multiple rules match.")
    @Effect(predicate = RuleEngine2PortsPmmlSettings.IsPmmlEnabled.class, type = EffectType.SHOW)
    @Persist(configKey = RuleEngine2PortsSettings.RULE_SELECTION_METHOD)
    RuleEngine2PortsSettings.RuleSelectionMethod m_ruleSelectionMethod =
        RuleEngine2PortsSettings.DEFAULT_RULE_SELECTION_METHOD;

    @Widget(title = "Use default value/score",
        description = "When checked, the default score/value specified below is used when no rules matched. "
            + "When unchecked, a missing value is produced instead.")
    @Effect(predicate = RuleEngine2PortsPmmlSettings.IsPmmlEnabled.class, type = EffectType.SHOW)
    @ValueReference(RuleEngine2PortsPmmlSettings.UseDefaultScoreRef.class)
    @Persist(configKey = RuleEngine2PortsSettings.HAS_DEFAULT_SCORE)
    boolean m_useDefaultScore = RuleEngine2PortsSettings.DEFAULT_HAS_DEFAULT_SCORE;

    @Widget(title = "Value", description = "The default score/value to use when no rules matched.")
    @Effect(predicate = RuleEngine2PortsPmmlSettings.IsPmmlAndUseDefaultScore.class, type = EffectType.SHOW)
    @Persist(configKey = RuleEngine2PortsSettings.DEFAULT_SCORE)
    String m_defaultScore = RuleEngine2PortsSettings.DEFAULT_DEFAULT_SCORE;

    @Widget(title = "Use default confidence value",
        description = "When checked, the default confidence value specified below is used for rules that do not "
            + "have a confidence value in the confidence column.")
    @Effect(predicate = RuleEngine2PortsPmmlSettings.IsPmmlEnabled.class, type = EffectType.SHOW)
    @ValueReference(RuleEngine2PortsPmmlSettings.UseDefaultConfidenceRef.class)
    @Persist(configKey = RuleEngine2PortsSettings.HAS_DEFAULT_CONFIDENCE)
    boolean m_useDefaultConfidence = RuleEngine2PortsSettings.DEFAULT_HAS_DEFAULT_CONFIDENCE;

    @Widget(title = "Value",
        description = "The default confidence value to use when it is not specified by the confidence column.")
    @NumberInputWidget(minValidation = IsNonNegativeValidation.class,
        maxValidation = RuleEngine2PortsPmmlSettings.IsAtMostOneValidation.class)
    @Effect(predicate = RuleEngine2PortsPmmlSettings.IsPmmlAndUseDefaultConfidence.class, type = EffectType.SHOW)
    @Persist(configKey = RuleEngine2PortsSettings.DEFAULT_CONFIDENCE)
    double m_defaultConfidence = RuleEngine2PortsSettings.DEFAULT_DEFAULT_CONFIDENCE;

    @Widget(title = "Rule confidence column",
        description = "Specifies confidence of the rules based on the values in the selected column.")
    @ChoicesProvider(RuleEngine2PortsPmmlSettings.RulesTableDoubleColumnsProvider.class)
    @Effect(predicate = RuleEngine2PortsPmmlSettings.IsPmmlEnabled.class, type = EffectType.SHOW)
    @Persistor(RuleEngine2PortsPmmlSettings.RuleConfidenceColumnPersistor.class)
    StringOrEnum<NoneChoice> m_ruleConfidenceColumn = new StringOrEnum<>(NONE);

    @Widget(title = "Use default weight value",
        description = "When checked, the default weight value specified below is used for rules that do not "
            + "have a weight value in the weight column.")
    @Effect(predicate = RuleEngine2PortsPmmlSettings.IsPmmlEnabled.class, type = EffectType.SHOW)
    @ValueReference(RuleEngine2PortsPmmlSettings.UseDefaultWeightRef.class)
    @Persist(configKey = RuleEngine2PortsSettings.HAS_DEFAULT_WEIGHT)
    boolean m_useDefaultWeight = RuleEngine2PortsSettings.DEFAULT_HAS_DEFAULT_WEIGHT;

    @Widget(title = "Value",
        description = "The default weight value to use when it is not specified by the weight column.")
    @NumberInputWidget(minValidation = IsNonNegativeValidation.class)
    @Effect(predicate = RuleEngine2PortsPmmlSettings.IsPmmlAndUseDefaultWeight.class, type = EffectType.SHOW)
    @Persist(configKey = RuleEngine2PortsSettings.DEFAULT_WEIGHT)
    double m_defaultWeight = RuleEngine2PortsSettings.DEFAULT_DEFAULT_WEIGHT;

    @Widget(title = "Rule weight column",
        description = "Specifies weight values for the rules based on the values in the selected column.")
    @ChoicesProvider(RuleEngine2PortsPmmlSettings.RulesTableDoubleColumnsProvider.class)
    @Effect(predicate = RuleEngine2PortsPmmlSettings.IsPmmlEnabled.class, type = EffectType.SHOW)
    @Persistor(RuleEngine2PortsPmmlSettings.RuleWeightColumnPersistor.class)
    StringOrEnum<NoneChoice> m_ruleWeightColumn = new StringOrEnum<>(NONE);

    @Widget(title = "Append confidence values",
        description = "When checked, confidence values are computed and written into a new column in the output table.")
    @Effect(predicate = RuleEngine2PortsPmmlSettings.IsPmmlEnabled.class, type = EffectType.SHOW)
    @ValueReference(RuleEngine2PortsPmmlSettings.ComputeConfidenceRef.class)
    @Persist(configKey = RuleEngine2PortsSettings.COMPUTE_CONFIDENCE)
    boolean m_computeConfidence = RuleEngine2PortsSettings.DEFAULT_COMPUTE_CONFIDENCE;

    @Widget(title = "Confidence column name", description = "The name of the column to write confidence values into.")
    @Effect(predicate = RuleEngine2PortsPmmlSettings.IsPmmlAndComputeConfidence.class, type = EffectType.SHOW)
    @Persist(configKey = RuleEngine2PortsSettings.PREDICTION_CONFIDENCE_COLUMN)
    String m_predictionConfidenceColumn = RuleEngine2PortsSettings.DEFAULT_PREDICTION_CONFIDENCE_COLUMN;

    @Widget(title = "Provide statistics",
        description = "When checked, <tt>recordCount</tt> (and if <b>Validation column</b> is selected, "
            + "also <tt>nbCorrect</tt>) is computed for the PMML output.")
    @Effect(predicate = RuleEngine2PortsPmmlSettings.IsPmmlEnabled.class, type = EffectType.SHOW)
    @ValueReference(RuleEngine2PortsPmmlSettings.ProvideStatisticsRef.class)
    @Persist(configKey = RuleEngine2PortsSettings.PROVIDE_STATISTICS)
    boolean m_provideStatistics = RuleEngine2PortsSettings.DEFAULT_IS_PROVIDE_STATISTICS;

    @Widget(title = "Validation column",
        description = "The column which is containing the correct prediction for the input (test/validation) table. "
            + "When <b>&lt;none&gt;</b> selected, no <tt>nbCorrect</tt> value will be computed.")
    @ChoicesProvider(RuleEngine2PortsPmmlSettings.DataTableAllColumnsForValidationProvider.class)
    @Effect(predicate = RuleEngine2PortsPmmlSettings.IsPmmlAndProvideStatistics.class, type = EffectType.SHOW)
    @Persistor(RuleEngine2PortsPmmlSettings.ValidateColumnPersistor.class)
    StringOrEnum<NoneChoice> m_validateColumn = new StringOrEnum<>(NONE);
}
