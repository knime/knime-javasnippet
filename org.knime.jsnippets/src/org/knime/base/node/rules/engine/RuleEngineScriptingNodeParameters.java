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
 *   5 Jan 2026 (Ali Asghar Marvi): created
 */
package org.knime.base.node.rules.engine;

import java.util.Optional;

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
import org.knime.node.parameters.migration.DefaultProvider;
import org.knime.node.parameters.migration.Migration;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
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
import org.knime.node.parameters.widget.message.TextMessage;

/**
 * This class registers and handles the generic configuration options for the Rule Engine node in modern UI.
 *
 * @author Ali Asghar Marvi, KNIME GmbH, Berlin, Germany
 * @since 5.10
 */

public final class RuleEngineScriptingNodeParameters implements NodeParameters {

    @TextMessage(WebUIDialogUtils.RuleEngineEditorAutoCompletionShortcutInfoMessageProvider.class)
    Void m_textMessage;

    @Persistor(RulesPersistor.class)
    String m_rules = "";

    @Widget(title = "Output column",
        description = "Choose whether to replace an existing column or append a new column to the table.")
    @ValueSwitchWidget
    @ValueReference(ReplaceOrAppendRef.class)
    @Persistor(ReplaceOrAppendPersistor.class)
    ReplaceOrAppend m_replaceOrAppend = ReplaceOrAppend.APPEND;

    /**
     * Parameter reference for the ReplaceOrAppend enum parameter in order to enable effects.
     */
    public class ReplaceOrAppendRef implements ParameterReference<ReplaceOrAppend> {
    }

    /**
     * Initialize a predicate that checks if the ReplaceOrAppend parameter is set to REPLACE.
     */
    public static final class IsReplace implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(ReplaceOrAppendRef.class).isOneOf(ReplaceOrAppend.REPLACE);
        }
    }

    @Widget(title = "Append column", description = "The name of the new column to append.")
    @Effect(predicate = IsReplace.class, type = EffectType.HIDE)
    @Persist(configKey = RuleEngineSettings.NEW_COLUMN_NAME)
    String m_newColName = "prediction";

    /**
     * Parameter reference for the column name to register a selection of compatible columns.
     */
    public static final class ColumnNameRef implements ParameterReference<String> {
    }

    /**
     * ValueProvider to auto-guess column selection if the node is not configured by the user.
     */
    public static final class ColumnNameProvider extends ColumnNameAutoGuessValueProvider {

        protected ColumnNameProvider() {
            super(ColumnNameRef.class);
        }

        @Override
        protected Optional<DataColumnSpec> autoGuessColumn(final NodeParametersInput parametersInput) {
            // select last default column as it was in old dialog
            final var compatibleColumns = ColumnSelectionUtil.getCompatibleColumnsOfFirstPort(parametersInput,
                StringValue.class, DoubleValue.class, IntValue.class);
            return compatibleColumns.isEmpty() ? Optional.empty()
                : Optional.of(compatibleColumns.get(compatibleColumns.size() - 1));
        }

    }

    @Widget(title = "Replace column", description = "The name of the column to replace.")
    @ChoicesProvider(AllColumnsProvider.class)
    @ValueReference(ColumnNameRef.class)
    @ValueProvider(ColumnNameProvider.class)
    @Effect(predicate = IsReplace.class, type = EffectType.SHOW)
    @Persist(configKey = RuleEngineSettings.REPLACE_COLUMN_NAME)
    String m_replaceColumn = "";

    /**
     *
     * Enum to handle the user selection of whether to append a new column or replace an existing column in the node.
     */
    public enum ReplaceOrAppend {

            @Label(value = "Append", description = "Append a new column to the table")
            APPEND, //

            @Label(value = "Replace", description = "Replace an existing column")
            REPLACE;
    }

    /**
     * Persistor for Enum of Replacing a column or appending a column.
     */
    public static final class ReplaceOrAppendPersistor extends EnumBooleanPersistor<ReplaceOrAppend> {
        ReplaceOrAppendPersistor() {
            super(RuleEngineSettings.APPEND_COLUMN, ReplaceOrAppend.class, ReplaceOrAppend.APPEND);
        }
    }

    /**
     * Custom Persistor for the rules, in order topersist rules as an array of strings.
     */
    public static final class RulesPersistor implements NodeParametersPersistor<String> {

        @Override
        public String load(final NodeSettingsRO settings) throws InvalidSettingsException {
            String[] rulesArray = settings.getStringArray(RuleEngineSettings.RULES, new String[0]);
            // Convert array of rules back to a single string with newlines
            return String.join("\n", rulesArray);
        }

        @Override
        public void save(final String param, final NodeSettingsWO settings) {
            // Convert single string with newlines back to array of rules.
            // The scripting editor returns a String object that contains all the contents of the scripting editor,
            // hence this split is needed, so that each line can be stored separately (compatible with NodeModel).
            String[] rulesArray = param.isEmpty() ? new String[0] : param.split("\n");
            settings.addStringArray(RuleEngineSettings.RULES, rulesArray);
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{RuleEngineSettings.RULES}};
        }
    }

    // migration added to be backwards compatible for any
    // KNIME workflow that uses a pre-3.2 version of this node.
    // Since this setting was introduced in KAP 3.2.
    @Migration(LoadTrueForOldNodes.class)
    boolean m_disallowLongOutputForCompatibility = false;

    /**
     * Migration logic to support config keys that were introduced after a certain KNIME version.
     */
    public static class LoadTrueForOldNodes implements DefaultProvider<Boolean> {
        @Override
        public Boolean getDefault() {
            return true;
        }
    }

}
