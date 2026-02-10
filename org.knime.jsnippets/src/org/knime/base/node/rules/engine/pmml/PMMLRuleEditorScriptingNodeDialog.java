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
 *   10 Feb 2026 (Ali Asghar Marvi): created
 */
package org.knime.base.node.rules.engine.pmml;

import java.util.Collections;

import org.knime.base.node.preproc.stringmanipulation.StringManipulatorProvider;
import org.knime.base.node.rules.engine.RuleEngineSettings;
import org.knime.base.node.util.WebUIDialogUtils;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.webui.node.dialog.scripting.AbstractDefaultScriptingNodeDialog;
import org.knime.core.webui.node.dialog.scripting.GenericInitialDataBuilder;
import org.knime.core.webui.node.dialog.scripting.WorkflowControl;

/**
 * WebUI dialog for the PMML Rule Editor node, defining autocompletion items and drag and drop insertion from the
 * available input columns and PMML functions.
 *
 * @author Ali Asghar Marvi, KNIME GmbH, Berlin, Germany
 */
@SuppressWarnings("restriction")
class PMMLRuleEditorScriptingNodeDialog extends AbstractDefaultScriptingNodeDialog {

    /**
     * Constructor for the PMML Rule Editor WebUI dialog.
     */
    protected PMMLRuleEditorScriptingNodeDialog() {
        super(PMMLRuleEditorScriptingNodeParameters.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected GenericInitialDataBuilder getInitialData(final NodeContext context) {
        var workflowControl = new WorkflowControl(context.getNodeContainer());
        return GenericInitialDataBuilder.createDefaultInitialDataBuilder(NodeContext.getContext()) //
            .addDataSupplier(WebUIDialogUtils.DATA_SUPPLIER_KEY_INPUT_OBJECTS, () -> WebUIDialogUtils.getFirstInputTableModel(workflowControl)) //
            .addDataSupplier(WebUIDialogUtils.DATA_SUPPLIER_KEY_FLOW_VARIABLES, () -> WebUIDialogUtils.getFlowVariablesInputOutputModel(workflowControl)) //
            .addDataSupplier(WebUIDialogUtils.DATA_SUPPLIER_KEY_OUTPUT_OBJECTS, Collections::emptyList) //
            .addDataSupplier(WebUIDialogUtils.DATA_SUPPLIER_KEY_LANGUAGE, () -> WebUIDialogUtils.DEFAULT_SCRIPT_LANGUAGE) //
            .addDataSupplier(WebUIDialogUtils.DATA_SUPPLIER_KEY_FILE_NAME, () -> WebUIDialogUtils.DEFAULT_SCRIPT_FILE_NAME) //
            .addDataSupplier(WebUIDialogUtils.DATA_SUPPLIER_KEY_MAIN_SCRIPT_CONFIG_KEY, () -> RuleEngineSettings.RULES) //
            .addDataSupplier(WebUIDialogUtils.DATA_SUPPLIER_KEY_STATIC_COMPLETION_ITEMS, () -> WebUIDialogUtils.getCompletionItems(workflowControl,
                StringManipulatorProvider.getDefault(), true));
    }

}
