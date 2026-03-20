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
import java.util.stream.Collectors;

import org.knime.base.node.jsnippet.expression.AbstractJSnippet;
import org.knime.base.node.jsnippet.expression.KnimeDoc;
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

    private final JavaSnippetScriptingService.FieldMappings m_fieldMappings;

    JavaSnippetCompletionService(final WorkflowControl workflowControl,
            final JavaSnippetScriptingService.FieldMappings fieldMappings) {
        m_workflowControl = workflowControl;
        m_fieldMappings = fieldMappings;
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

        // Input columns from configured field mappings
        addColumnCompletions(items);

        // Input flow variables from configured field mappings
        addFlowVariableCompletions(items);

        // Output columns from configured field mappings
        addOutputColumnCompletions(items);

        // Output flow variables from configured field mappings
        addOutputFlowVariableCompletions(items);

        // AbstractJSnippet methods and instance fields (ROWID, ROWINDEX, ROWCOUNT) via reflection + @KnimeDoc
        addSnippetMethodCompletions(items);

        // Java keywords
        for (var keyword : JAVA_KEYWORDS) {
            items.add(new DynamicCompletionItem(keyword, "Keyword", null, null,
                keyword, false, "9_kw_" + keyword, keyword));
        }

        return items;
    }

    private void addColumnCompletions(final List<DynamicCompletionItem> items) {
        var mappings = m_fieldMappings.inputColumns();
        if (mappings.length == 0) {
            return;
        }
        for (var mapping : mappings) {
            var doc = "Input column field (" + mapping.javaType() + ") \u2014 reads from KNIME column \""
                + mapping.knimeName() + "\"";
            items.add(new DynamicCompletionItem(
                mapping.javaName(), "Field", mapping.javaType(),
                doc, mapping.javaName(), false,
                "1_col_" + mapping.javaName(), mapping.javaName()));
        }
    }

    private void addFlowVariableCompletions(final List<DynamicCompletionItem> items) {
        var mappings = m_fieldMappings.inputFlowVariables();
        if (mappings.length == 0) {
            return;
        }
        for (var mapping : mappings) {
            var doc = "Input flow variable field (" + mapping.javaType() + ") \u2014 reads from KNIME flow variable \""
                + mapping.knimeName() + "\"";
            items.add(new DynamicCompletionItem(
                mapping.javaName(), "Field", mapping.javaType(),
                doc, mapping.javaName(), false,
                "2_fv_" + mapping.javaName(), mapping.javaName()));
        }
    }

    private void addOutputColumnCompletions(final List<DynamicCompletionItem> items) {
        var mappings = m_fieldMappings.outputColumns();
        if (mappings.length == 0) {
            return;
        }
        for (var mapping : mappings) {
            var doc = "Output column field (" + mapping.javaType() + ") \u2014 writes to KNIME column \""
                + mapping.knimeName() + "\"";
            items.add(new DynamicCompletionItem(
                mapping.javaName(), "Field", mapping.javaType(),
                doc, mapping.javaName(), false,
                "2_outcol_" + mapping.javaName(), mapping.javaName()));
        }
    }

    private void addOutputFlowVariableCompletions(final List<DynamicCompletionItem> items) {
        var mappings = m_fieldMappings.outputFlowVariables();
        if (mappings.length == 0) {
            return;
        }
        for (var mapping : mappings) {
            var doc = "Output flow variable field (" + mapping.javaType() + ") \u2014 writes to KNIME flow variable \""
                + mapping.knimeName() + "\"";
            items.add(new DynamicCompletionItem(
                mapping.javaName(), "Field", mapping.javaType(),
                doc, mapping.javaName(), false,
                "2_outfv_" + mapping.javaName(), mapping.javaName()));
        }
    }

    private static void addSnippetMethodCompletions(final List<DynamicCompletionItem> items) {
        // Methods via reflection + @KnimeDoc
        for (var method : AbstractJSnippet.class.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) {
                continue;
            }
            int mods = method.getModifiers();
            if (!Modifier.isPublic(mods) && !Modifier.isProtected(mods)) {
                continue;
            }
            // Skip internal/lifecycle methods not useful in snippet body
            var methodName = method.getName();
            if ("attachLogger".equals(methodName) || "snippet".equals(methodName)) {
                continue;
            }

            var doc = method.getAnnotation(KnimeDoc.class);
            String documentation = doc != null ? doc.value() : null;

            String params = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
            String returnType = method.getReturnType().getSimpleName();
            String label = methodName + "(" + params + ")";
            String insertParams = buildParamSnippet(method.getParameterTypes());

            items.add(new DynamicCompletionItem(
                label, "Method", returnType, documentation,
                methodName + "(" + insertParams + ")",
                !insertParams.isEmpty(),
                "3_method_" + methodName, methodName));
        }

        // Public instance fields (ROWID, ROWINDEX, ROWCOUNT) via reflection + @KnimeDoc
        for (var field : AbstractJSnippet.class.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            int mods = field.getModifiers();
            if (!Modifier.isPublic(mods)) {
                continue;
            }

            var doc = field.getAnnotation(KnimeDoc.class);
            String documentation = doc != null ? doc.value() : null;

            items.add(new DynamicCompletionItem(
                field.getName(), "Field", field.getType().getSimpleName(), documentation,
                field.getName(), false,
                "0_meta_" + field.getName(), field.getName()));
        }
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

            for (var method : targetClass.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                int methodMods = method.getModifiers();
                boolean includeProtected = targetClass == AbstractJSnippet.class;
                if (!Modifier.isPublic(methodMods)
                        && !(includeProtected && Modifier.isProtected(methodMods))) {
                    continue;
                }
                if (isStaticContext && !Modifier.isStatic(methodMods)) {
                    continue;
                }
                var paramTypes = Arrays.stream(method.getParameterTypes())
                    .map(Class::getSimpleName).toList();
                var snippet = buildParamSnippet(method.getParameterTypes());
                var signature = method.getName() + "(" + String.join(", ", paramTypes) + ")";
                var insertText = method.getName() + "(" + snippet + ")";
                var returnType = method.getReturnType().getSimpleName();
                var methodDoc = method.getAnnotation(KnimeDoc.class);
                String documentation = methodDoc != null ? methodDoc.value() : null;
                items.add(new DynamicCompletionItem(signature, "Method", returnType, documentation,
                    insertText, !snippet.isEmpty(), "0_" + method.getName(), method.getName()));
            }

            for (var field : targetClass.getFields()) {
                if (!Modifier.isPublic(field.getModifiers())) {
                    continue;
                }
                if (isStaticContext && !Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                var fieldDoc = field.getAnnotation(KnimeDoc.class);
                String fieldDocumentation = fieldDoc != null ? fieldDoc.value() : null;
                items.add(new DynamicCompletionItem(field.getName(), "Field",
                    field.getType().getSimpleName(), fieldDocumentation,
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
