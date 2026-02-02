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
 *   30 Jan 2026 (Ali Asghar Marvi): created
 */
package org.knime.base.node.preproc.stringmanipulation.multicolumn;

import java.util.List;

import org.knime.base.node.util.WebUIDialogUtils;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.IntValue;
import org.knime.core.data.StringValue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.persistence.legacy.LegacyColumnFilterPersistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ColumnChoicesProvider;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.choices.filter.ColumnFilter;
import org.knime.node.parameters.widget.choices.filter.ColumnFilterWidget;
import org.knime.node.parameters.widget.message.TextMessage;

/**
 * Configuration parameters in the WebUI dialog for the Multi Column String Manipulation node.
 *
 * @author Ali Asghar Marvi, KNIME GmbH, Berlin, Germany
 */
@SuppressWarnings("restriction")
class MultiColumnStringManipulationScriptingNodeParameters implements NodeParameters {

    // --- Layout ---

    @Section(title = "Output Settings")
    interface OutputSettingsSection {
    }

    @Section(title = "Error Handling")
    interface ErrorHandlingSection {
    }

    // --- Settings ---

    @TextMessage(WebUIDialogUtils.FunctionAutoCompletionShortcutInfoMessageProvider.class)
    Void m_textMessage;

    //need to use variable name that is same as config key
    String m_EXPRESSION = MultiColumnStringManipulationSettings.getCurrentColumnReference(); // NOSONAR: variable name must match constant

    /**
     * Choices provider for columns compatible with string, double, or integer values. These are the column types
     * supported by the multi-column string manipulation expression.
     */
    static final class CompatibleColumnsProvider implements ColumnChoicesProvider {
        @Override
        public List<DataColumnSpec> columnChoices(final NodeParametersInput context) {
            return context.getInTableSpec(0)
                .map(spec -> spec.stream()
                    .filter(col -> col.getType().isCompatible(StringValue.class)
                        || col.getType().isCompatible(DoubleValue.class) || col.getType().isCompatible(IntValue.class))
                    .toList())
                .orElse(List.of());
        }
    }

    @Widget(title = "Column selection",
        description = "The columns to iterate over. These columns can be dynamically referenced in the expression "
            + "using $$CURRENTCOLUMN$$. Dynamically referenced means that $$CURRENTCOLUMN$$ refers to different "
            + "columns, depending on which columns are selected. It is allowed to include non-string columns, "
            + "but their values will be converted to string before substituting them for $$CURRENTCOLUMN$$.")
    @ColumnFilterWidget(choicesProvider = CompatibleColumnsProvider.class)
    @Persistor(ColumnFilterPersistor.class)
    ColumnFilter m_columnSelection = new ColumnFilter();

    @Widget(title = "Output columns",
        description = "Choose whether to replace the selected input columns with the computed values "
            + "or to append the computed values as new columns.")
    @ValueSwitchWidget
    @ValueReference(AppendOrReplaceRef.class)
    @Persistor(AppendOrReplacePersistor.class)
    @Layout(OutputSettingsSection.class)
    AppendOrReplace m_appendOrReplace = AppendOrReplace.APPEND;

    @Widget(title = "Suffix for new columns",
        description = "The suffix to append to the original column names when creating new columns. "
            + "Only applicable when appending columns.")
    @Effect(predicate = IsAppend.class, type = EffectType.SHOW)
    @Persist(configKey = "APPEND_COLUMN_SUFFIX")
    @Layout(OutputSettingsSection.class)
    String m_appendedColumnsSuffix = "_transformed";

    @Widget(title = "Insert missing values as null",
        description = "If checked, missing values in the input columns will be inserted as null into the expression. "
            + "If unchecked, the expression is not evaluated if any of its inputs is missing. Instead, a missing "
            + "value is returned.")
    @Persist(configKey = "Insert missing values as null")
    @Layout(ErrorHandlingSection.class)
    boolean m_evaluateWithMissingValues = true;

    @Widget(title = "Fail if expression cannot be evaluated",
        description = "Whether to stop node execution when an expression cannot be evaluated. This could happen if "
            + "an input column contains values that the expression cannot handle (e.g., toInt() on a column "
            + "containing non-numeric strings). If unchecked, errors during evaluation will produce missing values "
            + "instead of failing the node.")
    @Persist(configKey = "Abort execution on evaluation errors")
    @Layout(ErrorHandlingSection.class)
    boolean m_failOnEvaluationException = true;

    // --- Enums and References ---

    enum AppendOrReplace {
            @Label(value = "Append as new columns",
                description = "Append the computed values as new columns using the original column name "
                    + "with the specified suffix.")
            APPEND, @Label(value = "Replace selected input columns",
                description = "Replace the values in the columns that were selected to loop over.")
            REPLACE;
    }

    static final class AppendOrReplaceRef implements ParameterReference<AppendOrReplace> {
    }

    static final class IsAppend implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(AppendOrReplaceRef.class).isOneOf(AppendOrReplace.APPEND);
        }
    }

    // --- Persistors ---

    /**
     * Custom persistor for the column filter that uses the legacy column filter format.
     */
    static final class ColumnFilterPersistor extends LegacyColumnFilterPersistor {
        ColumnFilterPersistor() {
            super("column_selection");
        }
    }

    /**
     * Custom persistor for the append/replace enum that persists it as a string matching the legacy settings format.
     */
    static final class AppendOrReplacePersistor implements NodeParametersPersistor<AppendOrReplace> {

        private static final String CONFIG_KEY = "APPEND_OR_REPLACE";

        @Override
        public AppendOrReplace load(final NodeSettingsRO settings) throws InvalidSettingsException {
            String value = settings.getString(CONFIG_KEY);
            return switch (value) {
                case MultiColumnStringManipulationSettings.APPEND_ACTION -> AppendOrReplace.APPEND;
                case MultiColumnStringManipulationSettings.REPLACE_ACTION -> AppendOrReplace.REPLACE;
                default -> throw new InvalidSettingsException("Unknown value for " + CONFIG_KEY + ": " + value);
            };
        }

        @Override
        public void save(final AppendOrReplace param, final NodeSettingsWO settings) {
            String value = param == AppendOrReplace.APPEND ? MultiColumnStringManipulationSettings.APPEND_ACTION
                : MultiColumnStringManipulationSettings.REPLACE_ACTION;
            settings.addString(CONFIG_KEY, value);
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{CONFIG_KEY}};
        }
    }

}
