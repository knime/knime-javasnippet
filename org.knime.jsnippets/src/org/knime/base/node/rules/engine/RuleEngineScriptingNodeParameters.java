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

import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataTableSpec;
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
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.choices.util.AllColumnsProvider;

/**
 *
 * @author Ali ASghar Marvi, KNIME GmbH, Berlin, Germany
 * @since 5.10
 */
final class RuleEngineScriptingNodeParameters implements NodeParameters {

    RuleEngineScriptingNodeParameters() {
        m_replaceColumn = "";
    }

    RuleEngineScriptingNodeParameters(final NodeParametersInput input) {
        // Add a sensible default to replace column selection. Although it is optional for now,
        // but if we change the node model to use defaults from NodeParameters class
        // in future and deprecate this node, then this will be needed.
        m_replaceColumn = input.getInTableSpec(0).stream().flatMap(DataTableSpec::stream)
            .filter(cSpec -> cSpec.getType().isCompatible(StringValue.class)).findAny().map(DataColumnSpec::getName)
            .orElse("");
    }

    @Persistor(RulesPersistor.class)
    String m_rules = "";

    @Widget(title = "Output column",
        description = "Choose whether to replace an existing column or append a new column to the table.")
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

    @Widget(title = "Append column", description = "The name of the new column to append.")
    @Effect(predicate = IsReplace.class, type = EffectType.HIDE)
    @Persist(configKey = "new-column-name")
    String m_newColName = "prediction";

    @Widget(title = "Replace column", description = "The name of the column to replace.")
    @ChoicesProvider(AllColumnsProvider.class)
    @Effect(predicate = IsReplace.class, type = EffectType.SHOW)
    @Persist(configKey = RuleEngineSettings.REPLACE_COLUMN_NAME)
    String m_replaceColumn = "";

    enum ReplaceOrAppend {
            @Label(value = "Append column", description = "Append a new column to the table")
            APPEND, //
            @Label(value = "Replace column", description = "Replace an existing column")
            REPLACE;
    }

    static final class ReplaceOrAppendPersistor extends EnumBooleanPersistor<ReplaceOrAppend> {
        ReplaceOrAppendPersistor() {
            super(RuleEngineSettings.APPEND_COLUMN, ReplaceOrAppend.class, ReplaceOrAppend.APPEND);
        }
    }

    static final class RulesPersistor implements NodeParametersPersistor<String> {
        private static final String CFG_RULES = "rules";

        @Override
        public String load(final NodeSettingsRO settings) throws InvalidSettingsException {
            String[] rulesArray = settings.getStringArray(CFG_RULES, new String[0]);
            // Convert array of rules back to a single string with newlines
            return String.join("\n", rulesArray);
        }

        @Override
        public void save(final String param, final NodeSettingsWO settings) {
            // Convert single string with newlines back to array of rules.
            // The scripting editor returns a String object that contains all the contents of the scripting editor
            //, hence this split is needed, so that each line can be stored separately.
            String[] rulesArray = param.isEmpty() ? new String[0] : param.split("\n");
            settings.addStringArray(CFG_RULES, rulesArray);
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{CFG_RULES}};
        }
    }

    // migration added to be backwards compatible for any
    // KNIME workflow that uses a pre-3.2 version of this node.
    // Since this setting was introduced in KAP 3.2.
    @Migration(LoadTrueForOldNodes.class)
    boolean m_disallowLongOutputForCompatibility = false;

    static class LoadTrueForOldNodes implements DefaultProvider<Boolean> {
        @Override
        public Boolean getDefault() {
            return true;
        }
    }
}
