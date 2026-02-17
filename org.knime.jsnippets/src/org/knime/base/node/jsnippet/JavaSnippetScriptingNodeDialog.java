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
 *   Created on Feb 16, 2026 by GitHub Copilot
 */
package org.knime.base.node.jsnippet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.knime.base.node.util.WebUIDialogUtils;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.webui.node.dialog.scripting.AbstractDefaultScriptingNodeDialog;
import org.knime.core.webui.node.dialog.scripting.GenericInitialDataBuilder;
import org.knime.core.webui.node.dialog.scripting.GenericInitialDataBuilder.ScriptSection;
import org.knime.core.webui.node.dialog.scripting.WorkflowControl;

/**
 * This class implements the configuration dialog for the Java Snippet node in the Modern UI.
 * It provides a multi-section script editor for Java code with separate sections for imports,
 * fields, and the main method body.
 *
 * @author GitHub Copilot
 * @author Carsten Haubold, KNIME GmbH, Konstanz, Germany
 * @since 5.11
 */
@SuppressWarnings("restriction")
public class JavaSnippetScriptingNodeDialog extends AbstractDefaultScriptingNodeDialog {

    /**
     * Creates a new Java Snippet scripting node dialog.
     */
    JavaSnippetScriptingNodeDialog() {
        super(JavaSnippetScriptingNodeParameters.class);
    }

    @Override
    protected GenericInitialDataBuilder getInitialData(final NodeContext context) {
        var workflowControl = new WorkflowControl(context.getNodeContainer());
        
        // Build the initial data with standard scripting editor fields
        var builder = GenericInitialDataBuilder.createDefaultInitialDataBuilder(context)
            .addDataSupplier(WebUIDialogUtils.DATA_SUPPLIER_KEY_INPUT_OBJECTS,
                () -> WebUIDialogUtils.getFirstInputTableModel(workflowControl))
            .addDataSupplier(WebUIDialogUtils.DATA_SUPPLIER_KEY_FLOW_VARIABLES,
                () -> WebUIDialogUtils.getFlowVariablesInputOutputModel(workflowControl))
            .addDataSupplier(WebUIDialogUtils.DATA_SUPPLIER_KEY_OUTPUT_OBJECTS,
                Collections::emptyList)
            .addDataSupplier(WebUIDialogUtils.DATA_SUPPLIER_KEY_LANGUAGE,
                () -> "java")
            .addDataSupplier(WebUIDialogUtils.DATA_SUPPLIER_KEY_FILE_NAME,
                () -> "JSnippet.java");

        // Add multi-section script configuration
        // This creates a Java class template with three editable sections
        // separated by read-only template code
        List<ScriptSection> scriptSections = new ArrayList<>();
        
        // Section 1: Editable imports
        scriptSections.add(new ScriptSection(true, "scriptImports"));
        
        // Read-only: Class declaration and opening
        scriptSections.add(new ScriptSection(false, 
            "\n\npublic class JSnippet extends AbstractJSnippet {\n"));
        
        // Section 2: Editable fields
        scriptSections.add(new ScriptSection(true, "scriptFields"));
        
        // Read-only: Method signature
        scriptSections.add(new ScriptSection(false,
            "\n\n  @Override\n" +
            "  public void snippet() throws TypeException, ColumnException, Abort {\n"));
        
        // Section 3: Editable body
        scriptSections.add(new ScriptSection(true, "scriptBody"));
        
        // Read-only: Closing braces
        scriptSections.add(new ScriptSection(false, "\n  }\n}\n"));
        
        // Add the script sections to the builder
        builder.addScriptSectionData(scriptSections);
        
        // TODO: Add static completion items for Java (imports, KNIME API, etc.)
        // builder.addDataSupplier(WebUIDialogUtils.DATA_SUPPLIER_KEY_STATIC_COMPLETION_ITEMS,
        //     () -> getJavaCompletionItems(workflowControl));
        
        return builder;
    }

    // TODO: Implement Java-specific completion items
    // private static List<CompletionItem> getJavaCompletionItems(WorkflowControl workflowControl) {
    //     List<CompletionItem> items = new ArrayList<>();
    //     // Add common Java imports
    //     // Add KNIME API completions
    //     // Add input column/variable completions
    //     return items;
    // }
}
