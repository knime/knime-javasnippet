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
 *   Mar 19, 2026 (Jochen Reißinger, TNG Technology Consulting GmbH): created
 */
package org.knime.base.node.rules.engine.twoports;

import java.util.List;

import org.knime.core.data.BooleanValue;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.StringValue;
import org.knime.core.node.NodeSettingsRO;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.migration.ConfigMigration;
import org.knime.node.parameters.migration.Migration;
import org.knime.node.parameters.migration.NodeParametersMigration;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.ColumnChoicesProvider;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.choices.util.CompatibleColumnsProvider;

/**
 * Shared node parameters for Rule-based nodes.
 * <p>
 * Rules and their outcomes are read from a second input port (a rules table). The dialog lets the user choose between
 * two <em>dictionary modes</em>:
 * <ul>
 * <li><b>Rule</b> – a single column contains the full rule expression including the outcome (e.g.
 * {@code $Col$ > 0 => TRUE}).</li>
 * <li><b>Condition and value</b> – a condition column contains only the condition part and a separate value column
 * contains the outcome.</li>
 * </ul>
 *
 * <h3>Backwards compatibility / migration</h3> Old workflows stored {@code rules.column} (always) and
 * {@code outcomes.column} (null when not set). The {@link DictionaryModeMigration} reads these old keys and populates
 * the new fields:
 * <ul>
 * <li>If {@code outcomes.column} was {@code null} → {@link DictionaryMode#RULE} with the value of {@code rules.column}
 * as {@link #m_ruleColumn}.</li>
 * <li>If {@code outcomes.column} was non-null → {@link DictionaryMode#CONDITION_AND_VALUE} with {@code rules.column} as
 * {@link #m_conditionColumn} and {@code outcomes.column} as {@link #m_valueColumn}.</li>
 * </ul>
 * The new format stores the mode under the config key {@value #CFG_DICTIONARY_MODE} and the columns under
 * {@value #CFG_RULE_COLUMN}, {@value #CFG_CONDITION_COLUMN}, and {@value #CFG_VALUE_COLUMN} respectively. The old
 * model reads the new format via updated {@code loadValidatedSettingsFrom}/{@code saveSettingsTo} methods in
 * {@link RuleEngine2PortsSimpleSettings}.
 *
 * @author Jochen Reißinger, TNG Technology Consulting GmbH
 * @since 5.12
 */
@SuppressWarnings("restriction")
abstract class AbstractRuleEngine2PortsNodeParameters implements NodeParameters {

    // -----------------------------------------------------------------------------------------------------------------
    // Config keys for the new format (written by this NodeParameters)
    // -----------------------------------------------------------------------------------------------------------------

    /** Config key for the dictionary mode switch. */
    static final String CFG_DICTIONARY_MODE = "dictionary.mode";

    /** Config key for the rule column (used when mode = RULE). */
    static final String CFG_RULE_COLUMN = "rule.column";

    /** Config key for the condition column (used when mode = CONDITION_AND_VALUE). */
    static final String CFG_CONDITION_COLUMN = "condition.column";

    /** Config key for the value / outcome column (used when mode = CONDITION_AND_VALUE). */
    static final String CFG_VALUE_COLUMN = "value.column";

    // -----------------------------------------------------------------------------------------------------------------
    // Column providers for the rules table (second input port, index 1)
    // -----------------------------------------------------------------------------------------------------------------

    /** Provides string columns from the rules table (port {@value RuleEngineFilter2PortsNodeModel#RULE_PORT}). */
    static final class RulesTableStringColumnsProvider extends CompatibleColumnsProvider {
        RulesTableStringColumnsProvider() {
            super(StringValue.class);
        }

        @Override
        public int getInputTableIndex(final NodeParametersInput parametersInput) {
            return RuleEngineFilter2PortsNodeModel.RULE_PORT;
        }
    }

    /**
     * Provides string, double, and boolean columns from the rules table (port
     * {@value RuleEngineFilter2PortsNodeModel#RULE_PORT}). Used for the value (outcome) column.
     */
    static final class RulesTableOutcomeColumnsProvider implements ColumnChoicesProvider {
        @Override
        public List<DataColumnSpec> columnChoices(final NodeParametersInput context) {
            return context.getInTableSpec(RuleEngineFilter2PortsNodeModel.RULE_PORT) //
                .map(spec -> spec.stream() //
                    .filter(col -> col.getType().isCompatible(StringValue.class)
                        || col.getType().isCompatible(DoubleValue.class)
                        || col.getType().isCompatible(BooleanValue.class)) //
                    .toList()) //
                .orElseGet(List::of);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Dictionary mode switch
    // -----------------------------------------------------------------------------------------------------------------

    /** Reference for the dictionary mode, used to drive show/hide effects. */
    interface DictionaryModeRef extends ParameterReference<DictionaryMode> {
    }

    /** Predicate: the mode is {@link DictionaryMode#RULE}. */
    static final class IsRuleMode implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(DictionaryModeRef.class).isOneOf(DictionaryMode.RULE);
        }
    }

    /**
     * Whether the rules table contains full rule expressions in one column, or splits conditions and values into two
     * separate columns.
     */
    enum DictionaryMode {
            @Label(value = "Rule",
                description = "A single column contains the full rule expression including the outcome "
                    + "(e.g. \"$Col$ > 0 => TRUE\").")
            RULE,

            @Label(value = "Condition and value",
                description = "Two separate columns are used: one for the condition part of the rule "
                    + "and one for the outcome value.")
            CONDITION_AND_VALUE;
    }

    /**
     * Migration that reads the old {@code rules.column} / {@code outcomes.column} format and decides which
     * {@link DictionaryMode} to use:
     * <ul>
     * <li>{@code outcomes.column} was absent or {@code null} → {@link DictionaryMode#RULE}</li>
     * <li>{@code outcomes.column} was a non-null string → {@link DictionaryMode#CONDITION_AND_VALUE}</li>
     * </ul>
     */
    static final class DictionaryModeMigration implements NodeParametersMigration<DictionaryMode> {
        @Override
        public List<ConfigMigration<DictionaryMode>> getConfigMigrations() {
            return List.of(ConfigMigration //
                .builder(AbstractRuleEngine2PortsNodeParameters::loadDictionaryModeFromLegacy) //
                .withDeprecatedConfigPath(RuleEngine2PortsSimpleSettings.RULES_COLUMN) //
                .build());
        }
    }

    private static DictionaryMode loadDictionaryModeFromLegacy(final NodeSettingsRO settings) {
        final String outcomeColumn = settings.getString(RuleEngine2PortsSimpleSettings.OUTCOMES_COLUMN, null);
        return (outcomeColumn == null || outcomeColumn.isBlank()) //
            ? DictionaryMode.RULE //
            : DictionaryMode.CONDITION_AND_VALUE;
    }

    @Widget(title = "Dictionary mode",
        description = "Choose how the rules table encodes the rules. "
            + "Select \"Rule\" when each row contains a full rule expression (condition => outcome). "
            + "Select \"Condition and value\" when the condition and the outcome are stored in two separate columns.")
    @ValueSwitchWidget
    @ValueReference(DictionaryModeRef.class)
    @Migration(DictionaryModeMigration.class)
    @Persist(configKey = CFG_DICTIONARY_MODE)
    DictionaryMode m_dictionaryMode = DictionaryMode.RULE;

    // -----------------------------------------------------------------------------------------------------------------
    // Rule mode: single rule column
    // -----------------------------------------------------------------------------------------------------------------

    /** Migration that reads {@code rules.column} from old settings for the rule column field. */
    static final class RuleColumnMigration implements NodeParametersMigration<String> {
        @Override
        public List<ConfigMigration<String>> getConfigMigrations() {
            return List.of(ConfigMigration //
                .builder(s -> s.getString(RuleEngine2PortsSimpleSettings.RULES_COLUMN, "")) //
                .withDeprecatedConfigPath(RuleEngine2PortsSimpleSettings.RULES_COLUMN) //
                .build());
        }
    }

    @Widget(title = "Rule column",
        description = "The column in the rules table that contains the full rule expressions "
            + "(condition and outcome combined, e.g. \"$Col$ > 0 => TRUE\").")
    @ChoicesProvider(RulesTableStringColumnsProvider.class)
    @Effect(predicate = IsRuleMode.class, type = EffectType.SHOW)
    @Migration(RuleColumnMigration.class)
    @Persist(configKey = CFG_RULE_COLUMN)
    String m_ruleColumn = "";

    // -----------------------------------------------------------------------------------------------------------------
    // Condition and value mode: two separate columns
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Migration that reads {@code rules.column} from old settings as the condition column (used when
     * {@code outcomes.column} was non-null in the old format).
     */
    static final class ConditionColumnMigration implements NodeParametersMigration<String> {
        @Override
        public List<ConfigMigration<String>> getConfigMigrations() {
            return List.of(ConfigMigration //
                .builder(s -> s.getString(RuleEngine2PortsSimpleSettings.RULES_COLUMN, "")) //
                .withDeprecatedConfigPath(RuleEngine2PortsSimpleSettings.RULES_COLUMN) //
                .build());
        }
    }

    @Widget(title = "Condition column",
        description = "The column in the rules table that contains the condition part of each rule "
            + "(e.g. \"$Col$ > 0\").")
    @ChoicesProvider(RulesTableStringColumnsProvider.class)
    @Effect(predicate = IsRuleMode.class, type = EffectType.HIDE)
    @Migration(ConditionColumnMigration.class)
    @Persist(configKey = CFG_CONDITION_COLUMN)
    String m_conditionColumn = "";

    /** Migration that reads {@code outcomes.column} from old settings as the value column. */
    static final class ValueColumnMigration implements NodeParametersMigration<String> {
        @Override
        public List<ConfigMigration<String>> getConfigMigrations() {
            return List.of(ConfigMigration //
                .builder(s -> s.getString(RuleEngine2PortsSimpleSettings.OUTCOMES_COLUMN, "")) //
                .withDeprecatedConfigPath(RuleEngine2PortsSimpleSettings.OUTCOMES_COLUMN) //
                .build());
        }
    }

    @Widget(title = "Value column",
        description = "The column in the rules table that contains the outcome value for each rule "
            + "(e.g. \"TRUE\" or \"FALSE\").")
    @ChoicesProvider(RulesTableOutcomeColumnsProvider.class)
    @Effect(predicate = IsRuleMode.class, type = EffectType.HIDE)
    @Migration(ValueColumnMigration.class)
    @Persist(configKey = CFG_VALUE_COLUMN)
    String m_valueColumn = "";

    @Widget(title = "Treat values starting with $ as references",
        description = "When checked, values in the value column that start with $ will be treated as references (for "
            + "example, column, flow variable, or table property references) rather than literal string values.")
    @Effect(predicate = IsRuleMode.class, type = EffectType.HIDE)
    @Persist(configKey = RuleEngine2PortsSimpleSettings.TREAT_OUTCOMES_WITH_DOLLAR_AS_REFEENCES)
    boolean m_treatOutcomesWithDollarAsReferences =
        RuleEngine2PortsSimpleSettings.DEFAULT_TREAT_OUTCOMES_WITH_DOLLAR_AS_REFERENCES;

    // -----------------------------------------------------------------------------------------------------------------
    // Hidden compatibility setting (pre-3.2 workflows)
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Compatibility flag introduced in KNIME 3.2 when {@code $$ROWINDEX$$} and {@code $$ROWCOUNT$$} were changed from
     * producing {@code IntCell} to {@code LongCell} output. When {@code true}, the node maps long values back to int
     * for backwards compatibility with pre-3.2 workflows.
     * <p>
     * The default is {@code false} (the Java default for {@code boolean}), which is correct for two reasons:
     * <ul>
     * <li>Brand new nodes created via the new dialog have no prior settings, so the default applies — and new nodes
     * should use long output.</li>
     * <li>Workflows previously saved by the old dialog always had this key written explicitly with the correct value,
     * so the default does not apply for those.</li>
     * </ul>
     * There is no widget for this field; it is a hidden persistence-only setting.
     */
    @Persist(configKey = "disallowLongOutputForCompatibility")
    boolean m_disallowLongOutputForCompatibility;
}
