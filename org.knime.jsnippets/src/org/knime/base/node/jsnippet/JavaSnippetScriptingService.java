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

import java.util.List;

import org.knime.core.webui.node.dialog.scripting.CodeGenerationRequest;
import org.knime.core.webui.node.dialog.scripting.DynamicCompletionItem;
import org.knime.core.webui.node.dialog.scripting.DynamicCompletionRequest;
import org.knime.core.webui.node.dialog.scripting.InputOutputModel;
import org.knime.core.webui.node.dialog.scripting.ScriptingService;
import org.knime.core.webui.node.dialog.scripting.WorkflowControl;

/**
 * Custom {@link ScriptingService} for the Java Snippet node that delegates {@code getCompletions} to
 * {@link JavaSnippetCompletionService}.
 *
 * @author GitHub Copilot
 * @author Carsten Haubold, KNIME GmbH, Konstanz, Germany
 * @since 5.11
 */
@SuppressWarnings("restriction")
public class JavaSnippetScriptingService extends ScriptingService {

    /**
     * Creates a new Java Snippet scripting service.
     *
     * @param workflowControl the workflow control for the node
     */
    public JavaSnippetScriptingService(final WorkflowControl workflowControl) {
        super(null, x -> true, workflowControl);
    }

    @Override
    public RpcService getJsonRpcService() {
        return new JavaSnippetRpcService();
    }

    /**
     * The JSON-RPC service implementation for the Java Snippet node.
     */
    public class JavaSnippetRpcService extends RpcService {

        @Override
        protected CodeGenerationRequest getCodeSuggestionRequest(final String userPrompt,
                final String currentCode, final InputOutputModel[] inputModels) {
            throw new UnsupportedOperationException(
                "Code suggestion is not supported for the Java Snippet node.");
        }

        @Override
        public List<DynamicCompletionItem> getCompletions(final DynamicCompletionRequest request) {
            return new JavaSnippetCompletionService(getWorkflowControl()).getCompletions(request);
        }
    }
}
