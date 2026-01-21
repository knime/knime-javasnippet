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

import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.migration.Migration;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.persistence.legacy.EnumBooleanPersistor;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.util.AllFlowVariablesProvider;

/**
 * This class registers and handles the generic configuration options for the Rule Engine Variable node in modern UI.
 *
 * @author Ali Asghar Marvi, KNIME GmbH, Berlin, Germany
 * @since 5.10
 */
final class RuleEngineVariableScriptingNodeParameters implements NodeParameters {

    @Persistor(RuleEngineScriptingNodeParameters.RulesPersistor.class)
    String m_rules = "";

    // using the persistor from this Java file since the enum is different than defined in RuleEngineScriptingNodeParameters
    @Persistor(ReplaceOrAppendPersistor.class)
    ReplaceOrAppend m_replaceOrAppend = ReplaceOrAppend.APPEND;

    @Widget(title = "New flow variable name", description = "The name of the new flow variable.")
    @Persist(configKey = RuleEngineSettings.NEW_COLUMN_NAME)
    String m_newVarName = "prediction";

    @ChoicesProvider(AllFlowVariablesProvider.class)
    @Persist(configKey = RuleEngineSettings.REPLACE_COLUMN_NAME)
    String m_replaceColumn = "";

    enum ReplaceOrAppend {
            APPEND, //
            REPLACE;
    }

    // need to keep it for flow variable settings
    static final class ReplaceOrAppendPersistor extends EnumBooleanPersistor<ReplaceOrAppend> {
        ReplaceOrAppendPersistor() {
            super(RuleEngineSettings.APPEND_COLUMN, ReplaceOrAppend.class, ReplaceOrAppend.APPEND);
        }
    }

    // migration added to be backwards compatible for any
    // KNIME workflow that uses a pre-3.2 version of this node.
    // Since this setting was introduced in KAP 3.2.
    @Migration(RuleEngineScriptingNodeParameters.LoadTrueForOldNodes.class)
    boolean m_disallowLongOutputForCompatibility = false;

}
