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
 *   Mar 24, 2026 (Jochen Reißinger, TNG Technology Consulting GmbH): created
 */
package org.knime.base.node.rules.engine.twoports;

import static org.knime.core.webui.node.dialog.defaultdialog.setting.singleselection.NoneChoice.NONE;

import java.util.List;

import org.knime.core.data.BooleanValue;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.StringValue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.webui.node.dialog.defaultdialog.setting.singleselection.NoneChoice;
import org.knime.core.webui.node.dialog.defaultdialog.setting.singleselection.StringOrEnum;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.widget.choices.ColumnChoicesProvider;
import org.knime.node.parameters.widget.choices.util.ColumnSelectionUtil;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation.MaxValidation;

/**
 * Helper class holding all inner types used by the PMML settings section of {@link RuleEngine2PortsNodeParameters}.
 *
 * @author Jochen Reißinger, TNG Technology Consulting GmbH
 */
@SuppressWarnings("restriction")
final class RuleEngine2PortsPmmlSettings {

    private RuleEngine2PortsPmmlSettings() {
    }

    interface IsPmmlEnabledRef extends ParameterReference<Boolean> {
    }

    static final class IsPmmlEnabled implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getBoolean(IsPmmlEnabledRef.class).isTrue();
        }
    }

    static final class RulesTableDoubleColumnsProvider implements ColumnChoicesProvider {
        @Override
        public List<DataColumnSpec> columnChoices(final NodeParametersInput context) {
            return context.getInTableSpec(RuleEngineFilter2PortsNodeModel.RULE_PORT) //
                .map(spec -> ColumnSelectionUtil.getCompatibleColumns(spec, DoubleValue.class)) //
                .orElseGet(List::of);
        }
    }

    static final class DataTableAllColumnsForValidationProvider implements ColumnChoicesProvider {
        @Override
        public List<DataColumnSpec> columnChoices(final NodeParametersInput context) {
            return ColumnSelectionUtil.getCompatibleColumns(context, RuleEngine2PortsNodeModel.DATA_PORT,
                StringValue.class, BooleanValue.class, DoubleValue.class);
        }
    }

    interface UseDefaultScoreRef extends ParameterReference<Boolean> {
    }

    static final class IsPmmlAndUseDefaultScore implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getBoolean(IsPmmlEnabledRef.class).isTrue().and(i.getBoolean(UseDefaultScoreRef.class).isTrue());
        }
    }

    interface UseDefaultConfidenceRef extends ParameterReference<Boolean> {
    }

    static final class IsAtMostOneValidation extends MaxValidation {
        @Override
        protected double getMax() {
            return 1;
        }
    }

    static final class IsPmmlAndUseDefaultConfidence implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getBoolean(IsPmmlEnabledRef.class).isTrue()
                .and(i.getBoolean(UseDefaultConfidenceRef.class).isTrue());
        }
    }

    interface UseDefaultWeightRef extends ParameterReference<Boolean> {
    }

    static final class IsPmmlAndUseDefaultWeight implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getBoolean(IsPmmlEnabledRef.class).isTrue().and(i.getBoolean(UseDefaultWeightRef.class).isTrue());
        }
    }

    interface ComputeConfidenceRef extends ParameterReference<Boolean> {
    }

    static final class IsPmmlAndComputeConfidence implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getBoolean(IsPmmlEnabledRef.class).isTrue().and(i.getBoolean(ComputeConfidenceRef.class).isTrue());
        }
    }

    interface ProvideStatisticsRef extends ParameterReference<Boolean> {
    }

    static final class IsPmmlAndProvideStatistics implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getBoolean(IsPmmlEnabledRef.class).isTrue().and(i.getBoolean(ProvideStatisticsRef.class).isTrue());
        }
    }

    private abstract static class NullableStringPersistor implements NodeParametersPersistor<StringOrEnum<NoneChoice>> {

        private final String m_configKey;

        NullableStringPersistor(final String configKey) {
            m_configKey = configKey;
        }

        @Override
        public StringOrEnum<NoneChoice> load(final NodeSettingsRO settings) throws InvalidSettingsException {
            final String value = settings.getString(m_configKey, null);
            return value == null ? new StringOrEnum<>(NONE) : new StringOrEnum<>(value);
        }

        @Override
        public void save(final StringOrEnum<NoneChoice> param, final NodeSettingsWO settings) {
            settings.addString(m_configKey, param.getEnumChoice().isPresent() ? null : param.getStringChoice());
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{m_configKey}};
        }
    }

    static final class RuleConfidenceColumnPersistor extends NullableStringPersistor {
        RuleConfidenceColumnPersistor() {
            super(RuleEngine2PortsSettings.CONFIDENCE_COLUMN);
        }
    }

    static final class RuleWeightColumnPersistor extends NullableStringPersistor {
        RuleWeightColumnPersistor() {
            super(RuleEngine2PortsSettings.WEIGHT_COLUMN);
        }
    }

    static final class ValidateColumnPersistor extends NullableStringPersistor {
        ValidateColumnPersistor() {
            super(RuleEngine2PortsSettings.VALIDATE_COLUMN);
        }
    }
}
