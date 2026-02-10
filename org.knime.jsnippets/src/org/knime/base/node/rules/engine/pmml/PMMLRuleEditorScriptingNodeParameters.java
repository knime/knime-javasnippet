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
 *   10 Feb 2026 (ali): created
 */
package org.knime.base.node.rules.engine.pmml;

import org.knime.base.node.rules.engine.RuleEngineScriptingNodeParameters;
import org.knime.base.node.rules.engine.RuleEngineScriptingNodeParameters.ReplaceOrAppend;
import org.knime.base.node.rules.engine.RuleEngineSettings;
import org.knime.base.node.util.WebUIDialogUtils;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.migration.Migration;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.choices.util.AllColumnsProvider;
import org.knime.node.parameters.widget.message.TextMessage;

/**
 * This class registers and handles the generic configuration options for the Ruleset Editor node in modern UI.
 *
 * @author Ali Asghar Marvi, KNIME GmbH, Berlin, Germany
 */
class PMMLRuleEditorScriptingNodeParameters implements NodeParameters {

    @TextMessage(WebUIDialogUtils.RuleEngineEditorAutoCompletionShortcutInfoMessageProvider.class)
    Void m_textMessage;

    @Persistor(RuleEngineScriptingNodeParameters.RulesPersistor.class)
    String m_rules = "";

    @Widget(title = "Output column",
        description = "Choose whether to replace an existing column or append a new column to the table.")
    @ValueSwitchWidget
    @ValueReference(RuleEngineScriptingNodeParameters.ReplaceOrAppendRef.class)
    @Persistor(RuleEngineScriptingNodeParameters.ReplaceOrAppendPersistor.class)
    RuleEngineScriptingNodeParameters.ReplaceOrAppend m_replaceOrAppend = ReplaceOrAppend.APPEND;

    @Widget(title = "Append column", description = "The name of the new column to append.")
    @Effect(predicate = RuleEngineScriptingNodeParameters.IsReplace.class, type = EffectType.HIDE)
    @Persist(configKey = RuleEngineSettings.NEW_COLUMN_NAME)
    String m_newColName = "prediction";

    @Widget(title = "Replace column", description = "The name of the column to replace.")
    @ChoicesProvider(AllColumnsProvider.class)
    @ValueReference(RuleEngineScriptingNodeParameters.ColumnNameRef.class)
    @ValueProvider(RuleEngineScriptingNodeParameters.ColumnNameProvider.class)
    @Effect(predicate = RuleEngineScriptingNodeParameters.IsReplace.class, type = EffectType.SHOW)
    @Persist(configKey = RuleEngineSettings.REPLACE_COLUMN_NAME)
    String m_replaceColumn = "";

    // migration added to be backwards compatible for any
    // KNIME workflow that uses a pre-3.2 version of this node.
    // Since this setting was introduced in KAP 3.2.
    @Migration(RuleEngineScriptingNodeParameters.LoadTrueForOldNodes.class)
    boolean m_disallowLongOutputForCompatibility = false;

}
