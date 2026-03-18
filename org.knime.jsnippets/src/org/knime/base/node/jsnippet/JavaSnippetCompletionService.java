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
 *   Created on Mar 18, 2026 by Carsten Haubold
 */
package org.knime.base.node.jsnippet;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.knime.base.node.jsnippet.expression.AbstractJSnippet;
import org.knime.base.node.util.WebUIDialogUtils;
import org.knime.core.data.DataTableSpec;
import org.knime.core.webui.node.dialog.scripting.DynamicCompletionItem;
import org.knime.core.webui.node.dialog.scripting.DynamicCompletionRequest;
import org.knime.core.webui.node.dialog.scripting.WorkflowControl;

/**
 * Provides dynamic autocompletion items for the Java Snippet editor.
 * <p>
 * <b>P0 completions</b> (always returned): row metadata fields (ROWID, ROWINDEX, ROWCOUNT), input table columns,
 * available flow variables, {@link AbstractJSnippet} methods accessible inside the snippet body, and Java keywords.
 * <p>
 * <b>P1 completions</b> (dot-triggered, context-aware): when the cursor follows a {@code .}, the service attempts to
 * resolve the type of the expression before the dot via reflection and returns matching public methods and fields. On
 * any failure the service falls back to P0.
 *
 * @author Carsten Haubold, KNIME GmbH, Konstanz, Germany
 * @since 5.11
 */
@SuppressWarnings("restriction")
final class JavaSnippetCompletionService {

    private static final List<String> JAVA_KEYWORDS = List.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new", "null",
        "package", "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while", "true", "false");

    private final WorkflowControl m_workflowControl;

    JavaSnippetCompletionService(final WorkflowControl workflowControl) {
        m_workflowControl = workflowControl;
    }

    List<DynamicCompletionItem> getCompletions(final DynamicCompletionRequest request) {
        try {
            boolean isDotTriggered = ".".equals(request.triggerCharacter());
            if (!isDotTriggered) {
                // Also activate P1 when the user manually invokes completion (Ctrl+Space)
                // with the cursor immediately after a dot.
                String[] lines = request.text().split("\n", -1);
                int lineIdx = request.cursorLine() - 1;
                int colIdx = request.cursorColumn() - 2; // -1 for 1-based, -1 for char before cursor
                if (lineIdx >= 0 && lineIdx < lines.length
                        && colIdx >= 0 && colIdx < lines[lineIdx].length()) {
                    isDotTriggered = lines[lineIdx].charAt(colIdx) == '.';
                }
            }
            if (isDotTriggered) {
                var p1Items = getP1Completions(request);
                if (!p1Items.isEmpty()) {
                    return p1Items;
                }
            }
            return getP0Completions();
        } catch (Exception e) { // NOSONAR - graceful degradation for auto-completion
            return getP0Completions();
        }
    }

    // ─── P0: always-available completions ────────────────────────────────────

    private List<DynamicCompletionItem> getP0Completions() {
        var items = new ArrayList<DynamicCompletionItem>();

        // Row metadata
        items.add(new DynamicCompletionItem("ROWID", "Field", "String",
            "The ID of the current row", "ROWID", false, "0_meta_ROWID", "ROWID"));
        items.add(new DynamicCompletionItem("ROWINDEX", "Field", "int",
            "The 0-based index of the current row", "ROWINDEX", false, "0_meta_ROWINDEX", "ROWINDEX"));
        items.add(new DynamicCompletionItem("ROWCOUNT", "Field", "int",
            "The total number of rows in the input table", "ROWCOUNT", false, "0_meta_ROWCOUNT", "ROWCOUNT"));

        // Input columns
        addColumnCompletions(items);

        // Flow variables
        addFlowVariableCompletions(items);

        // AbstractJSnippet methods
        addSnippetMethodCompletions(items);

        // Java keywords
        for (var keyword : JAVA_KEYWORDS) {
            items.add(new DynamicCompletionItem(keyword, "Keyword", null, null,
                keyword, false, "9_kw_" + keyword, keyword));
        }

        return items;
    }

    private void addColumnCompletions(final List<DynamicCompletionItem> items) {
        var inputSpec = m_workflowControl.getInputSpec();
        if (inputSpec == null || inputSpec.length == 0) {
            return;
        }
        if (inputSpec[0] instanceof DataTableSpec tableSpec) {
            for (var col : tableSpec) {
                var colName = col.getName();
                var typeName = col.getType().getCellClass().getSimpleName();
                var label = "$" + colName + "$";
                items.add(new DynamicCompletionItem(label, "Variable", typeName,
                    "Column: " + colName, label, false, "1_col_" + colName, colName));
            }
        }
    }

    private void addFlowVariableCompletions(final List<DynamicCompletionItem> items) {
        var stack = m_workflowControl.getFlowObjectStack();
        if (stack == null) {
            return;
        }
        var flowVars = stack.getAllAvailableFlowVariables();
        if (flowVars == null) {
            return;
        }
        for (var fv : flowVars.values()) {
            if (!WebUIDialogUtils.SUPPORTED_VARIABLE_TYPES.contains(fv.getVariableType())) {
                continue;
            }
            try {
                var typeChar = WebUIDialogUtils.getFlowVariableTypePrefix(fv);
                var varName = fv.getName();
                var label = "$${" + typeChar + varName + "}$$";
                var typeName = fv.getVariableType().getIdentifier();
                items.add(new DynamicCompletionItem(label, "Variable", typeName,
                    "Flow variable: " + varName, label, false, "2_fv_" + varName, varName));
            } catch (IllegalArgumentException ex) { // NOSONAR - skip unsupported types
            }
        }
    }

    private static void addSnippetMethodCompletions(final List<DynamicCompletionItem> items) {
        // getCell variants
        items.add(makeSnippetMethod("getCell(${1:column}, ${2:type})", "getCell", "T",
            "Get the value of a column by name", "getCell_name"));
        items.add(makeSnippetMethod("getCell(${1:colIndex}, ${2:type})", "getCell", "T",
            "Get the value of a column by index", "getCell_idx"));

        // isType variants
        items.add(makeSnippetMethod("isType(${1:column}, ${2:type})", "isType", "boolean",
            "Returns true when the column is of the given type", "isType_name"));
        items.add(makeSnippetMethod("isType(${1:colIndex}, ${2:type})", "isType", "boolean",
            "Returns true when the column is of the given type by index", "isType_idx"));

        // isMissing variants
        items.add(makeSnippetMethod("isMissing(${1:column})", "isMissing", "boolean",
            "Returns true when the cell of the given column is a missing cell", "isMissing_name"));
        items.add(makeSnippetMethod("isMissing(${1:colIndex})", "isMissing", "boolean",
            "Returns true when the cell at the given column index is a missing cell", "isMissing_idx"));

        // Column utilities
        items.add(makeSnippetMethod("getColumnCount()", "getColumnCount", "int",
            "Get the number of input columns", "getColumnCount"));
        items.add(makeSnippetMethod("getColumnName(${1:index})", "getColumnName", "String",
            "Get the name of the column at the specified index", "getColumnName"));
        items.add(makeSnippetMethod("columnExists(${1:column})", "columnExists", "boolean",
            "Returns true when a column with the given name exists", "columnExists_name"));
        items.add(makeSnippetMethod("columnExists(${1:index})", "columnExists", "boolean",
            "Returns true when a column at the given index exists", "columnExists_idx"));

        // Flow variable utilities
        items.add(makeSnippetMethod("getFlowVariable(${1:var}, ${2:type})", "getFlowVariable", "T",
            "Get the value of the flow variable with the given name", "getFlowVariable"));
        items.add(makeSnippetMethod("getFlowVariables(${1:type})", "getFlowVariables", "Map<String, T>",
            "Get all flow variables of the given type as a map", "getFlowVariables"));
        items.add(makeSnippetMethod("flowVariableExists(${1:name})", "flowVariableExists", "boolean",
            "Check if a flow variable with the given name exists", "flowVariableExists"));
        items.add(makeSnippetMethod("isFlowVariableOfType(${1:name}, ${2:t})", "isFlowVariableOfType", "boolean",
            "Check if a flow variable is of the given type", "isFlowVariableOfType"));

        // Logging methods (single-arg and throwable variants)
        for (var method : new String[]{"logWarn", "logDebug", "logInfo", "logError", "logFatal"}) {
            var level = method.substring(3).toLowerCase();
            items.add(makeSnippetMethod(method + "(${1:o})", method, "void",
                "Write a " + level + " message to the node logger", method + "_msg"));
            items.add(makeSnippetMethod(method + "(${1:o}, ${2:t})", method, "void",
                "Write a " + level + " message with a throwable to the node logger", method + "_ex"));
        }
    }

    /**
     * Builds a {@link DynamicCompletionItem} for an {@link AbstractJSnippet} method. The visible label is derived by
     * stripping snippet placeholder syntax from {@code insertText}.
     */
    private static DynamicCompletionItem makeSnippetMethod(final String insertText, final String filterText,
            final String returnType, final String doc, final String sortKey) {
        var label = insertText.replaceAll("\\$\\{\\d+:(.*?)\\}", "$1");
        return new DynamicCompletionItem(label, "Method", returnType, doc,
            insertText, true, "3_method_" + sortKey, filterText);
    }

    // ─── P1: context-aware completions after "." ─────────────────────────────

    private List<DynamicCompletionItem> getP1Completions(final DynamicCompletionRequest request) {
        var text = request.text();
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        var lines = text.split("\n", -1);
        var cursorLine = Math.max(0, Math.min(request.cursorLine() - 1, lines.length - 1));
        var cursorCol = Math.max(0, Math.min(request.cursorColumn() - 1, lines[cursorLine].length()));
        var lineUpToCursor = lines[cursorLine].substring(0, cursorCol);

        var prefix = extractPrefixBeforeDot(lineUpToCursor);
        if (prefix == null || prefix.isEmpty()) {
            return List.of();
        }

        return reflectCompletions(text, prefix);
    }

    /**
     * Extracts the identifier immediately before the trailing {@code .} in {@code line}. Returns {@code null} when
     * this cannot be determined (e.g., the line ends with a closing parenthesis from a method chain).
     */
    private static String extractPrefixBeforeDot(final String line) {
        var trimmed = line.stripTrailing();
        if (!trimmed.endsWith(".")) {
            return null;
        }
        trimmed = trimmed.substring(0, trimmed.length() - 1);
        var matcher = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*$").matcher(trimmed);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * Reflects on the class resolved for {@code prefix} in the given source text and returns completion items for all
     * accessible public methods and fields.
     */
    private List<DynamicCompletionItem> reflectCompletions(final String sourceText, final String prefix) {
        try {
            var targetClass = resolveClass(sourceText, prefix);
            if (targetClass == null) {
                return List.of();
            }

            var items = new ArrayList<DynamicCompletionItem>();
            // Heuristic: treat an upper-case prefix as a class name (static context)
            var isStaticContext = Character.isUpperCase(prefix.charAt(0));

            for (var method : targetClass.getMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                if (isStaticContext && !Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                var paramTypes = Arrays.stream(method.getParameterTypes())
                    .map(Class::getSimpleName).toList();
                var snippet = buildParamSnippet(method.getParameterTypes());
                var signature = method.getName() + "(" + String.join(", ", paramTypes) + ")";
                var insertText = method.getName() + "(" + snippet + ")";
                var returnType = method.getReturnType().getSimpleName();
                items.add(new DynamicCompletionItem(signature, "Method", returnType, null,
                    insertText, !snippet.isEmpty(), "0_" + method.getName(), method.getName()));
            }

            for (var field : targetClass.getFields()) {
                if (!Modifier.isPublic(field.getModifiers())) {
                    continue;
                }
                if (isStaticContext && !Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                items.add(new DynamicCompletionItem(field.getName(), "Field",
                    field.getType().getSimpleName(), null,
                    field.getName(), false, "1_" + field.getName(), field.getName()));
            }

            return items;
        } catch (Exception e) { // NOSONAR - graceful degradation
            return List.of();
        }
    }

    private static String buildParamSnippet(final Class<?>[] paramTypes) {
        if (paramTypes.length == 0) {
            return "";
        }
        var sb = new StringBuilder();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("${").append(i + 1).append(':').append(paramTypes[i].getSimpleName()).append('}');
        }
        return sb.toString();
    }

    /**
     * Attempts to resolve the Java class for {@code prefix}. Resolution order:
     * <ol>
     * <li>{@code "this"} → {@link AbstractJSnippet}</li>
     * <li>Matching {@code import} statement in the source</li>
     * <li>Variable type declaration found in the source (then resolved again via imports or {@code java.lang})</li>
     * <li>Direct class-name lookups in {@code ""}, {@code "java.lang."}, {@code "java.util."}</li>
     * </ol>
     */
    private Class<?> resolveClass(final String sourceText, final String prefix) {
        if ("this".equals(prefix)) {
            return AbstractJSnippet.class;
        }

        var fromImports = resolveFromImports(sourceText, prefix);
        if (fromImports != null) {
            return fromImports;
        }

        var declaredType = findVariableType(sourceText, prefix);
        if (declaredType != null) {
            var resolvedType = resolveFromImports(sourceText, declaredType);
            if (resolvedType != null) {
                return resolvedType;
            }
            try {
                return Class.forName("java.lang." + declaredType);
            } catch (ClassNotFoundException ignored) { // NOSONAR
            }
        }

        var bundleCl = JavaSnippetCompletionService.class.getClassLoader();
        var threadCl = Thread.currentThread().getContextClassLoader();
        for (var pkg : new String[]{"", "java.lang.", "java.util."}) {
            try {
                return Class.forName(pkg + prefix, false, bundleCl);
            } catch (ClassNotFoundException ignored) { // NOSONAR
            }
            if (threadCl != bundleCl) {
                try {
                    return Class.forName(pkg + prefix, false, threadCl);
                } catch (ClassNotFoundException ignored) { // NOSONAR
                }
            }
        }

        return null;
    }

    private static Class<?> resolveFromImports(final String sourceText, final String simpleName) {
        var importPattern = Pattern.compile(
            "^import\\s+([\\w.]+\\." + Pattern.quote(simpleName) + ")\\s*;",
            Pattern.MULTILINE);
        var matcher = importPattern.matcher(sourceText);
        if (matcher.find()) {
            var fqn = matcher.group(1);
            var bundleCl = JavaSnippetCompletionService.class.getClassLoader();
            try {
                return Class.forName(fqn, false, bundleCl);
            } catch (ClassNotFoundException ignored) { // NOSONAR
            }
            var threadCl = Thread.currentThread().getContextClassLoader();
            if (threadCl != bundleCl) {
                try {
                    return Class.forName(fqn, false, threadCl);
                } catch (ClassNotFoundException ignored) { // NOSONAR
                }
            }
        }
        return null;
    }

    /** Searches for a simple {@code TypeName varName} declaration in the source and returns {@code TypeName}. */
    private static String findVariableType(final String sourceText, final String varName) {
        var varPattern = Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\s+" + Pattern.quote(varName) + "\\b");
        var matcher = varPattern.matcher(sourceText);
        return matcher.find() ? matcher.group(1) : null;
    }
}
