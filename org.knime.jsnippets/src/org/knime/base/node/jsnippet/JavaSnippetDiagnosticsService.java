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
 *   Created on Mar 23, 2026 by Carsten Haubold
 */
package org.knime.base.node.jsnippet;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;

import org.apache.commons.io.FilenameUtils;
import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.knime.base.node.jsnippet.expression.AbstractJSnippet;
import org.knime.base.node.jsnippet.expression.Abort;
import org.knime.base.node.jsnippet.expression.Cell;
import org.knime.base.node.jsnippet.expression.ColumnException;
import org.knime.base.node.jsnippet.expression.TypeException;
import org.knime.core.node.NodeLogger;
import org.knime.core.webui.node.dialog.scripting.DiagnosticItem;
import org.knime.core.webui.node.dialog.scripting.DynamicCompletionRequest;

/**
 * Provides on-the-fly Java compilation diagnostics for the Java Snippet scripting editor.
 * <p>
 * The editor sends the full assembled source text (user imports + class template with read-only anchors + user fields +
 * method body). This service prepends the system imports that {@link JavaSnippet} always adds, compiles the result
 * with the Eclipse JDT compiler, and maps diagnostic line numbers back to the editor's coordinate space.
 * <p>
 * <b>Known limitation:</b> KNIME-mapped Java fields (generated from column/flow-variable configurations) are not
 * visible to the compiler, so diagnostics may include false-positive "undefined variable" errors for those fields.
 *
 * @author Carsten Haubold, KNIME GmbH, Konstanz, Germany
 * @since 5.11
 */
@SuppressWarnings("restriction")
final class JavaSnippetDiagnosticsService {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(JavaSnippetDiagnosticsService.class);

    /**
     * System imports that {@link JavaSnippet#getSystemImports()} returns, formatted as {@code import X;\n} lines.
     * Must be kept in sync with {@link JavaSnippet#getSystemImports()}.
     */
    static final String SYSTEM_IMPORTS_PREAMBLE;

    /** Number of newlines in {@link #SYSTEM_IMPORTS_PREAMBLE}, i.e. the number of lines to subtract from diagnostics. */
    static final int SYSTEM_IMPORTS_LINE_COUNT;

    static {
        final String pkg = "org.knime.base.node.jsnippet.expression";
        final String[] imports = {
            AbstractJSnippet.class.getName(),
            Abort.class.getName(),
            Cell.class.getName(),
            ColumnException.class.getName(),
            TypeException.class.getName(),
            "static " + pkg + ".Type.*",
            "java.util.Date",
            "java.util.Calendar",
            "org.w3c.dom.Document"
        };
        final StringBuilder sb = new StringBuilder();
        for (final String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
        }
        SYSTEM_IMPORTS_PREAMBLE = sb.toString();
        SYSTEM_IMPORTS_LINE_COUNT = (int)SYSTEM_IMPORTS_PREAMBLE.chars().filter(c -> c == '\n').count();
    }

    /** Lazily initialised compile-time classpath string, shared across all instances. */
    private static final AtomicReference<String> CLASSPATH_CACHE = new AtomicReference<>();

    /**
     * Compiles the editor content and returns any diagnostics.
     *
     * @param request the request carrying the current editor text
     * @return a list of {@link DiagnosticItem}s; never {@code null}
     */
    List<DiagnosticItem> getDiagnostics(final DynamicCompletionRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            return Collections.emptyList();
        }
        try {
            final String fullSource = SYSTEM_IMPORTS_PREAMBLE + request.text();
            final String classpath = getClasspath();
            return compile(fullSource, classpath, SYSTEM_IMPORTS_LINE_COUNT);
        } catch (IOException e) {
            LOGGER.debug("Failed to compile Java Snippet for diagnostics", e);
            return Collections.emptyList();
        }
    }

    // ─── Compilation ────────────────────────────────────────────────────────

    private static List<DiagnosticItem> compile(final String fullSource, final String classpath,
            final int lineOffset) throws IOException {
        final EclipseCompiler compiler = new EclipseCompiler();
        final DiagnosticCollector<JavaFileObject> diagCollector = new DiagnosticCollector<>();

        final List<String> compileArgs = new ArrayList<>();
        compileArgs.add("-classpath");
        compileArgs.add(classpath);
        compileArgs.add("-source");
        compileArgs.add("11");
        compileArgs.add("-target");
        compileArgs.add("11");
        compileArgs.add("-encoding");
        compileArgs.add("UTF-8");
        compileArgs.add("-proc:none");

        try (StandardJavaFileManager fileManager =
            compiler.getStandardFileManager(diagCollector, null, null)) {
            final CompilationTask task = compiler.getTask(new StringWriter(), fileManager, diagCollector, compileArgs,
                null, Collections.singletonList(new InMemoryJavaSource("JSnippet", fullSource)));
            task.call();
        }

        final int[] lineStarts = computeLineStarts(fullSource);

        final List<DiagnosticItem> result = new ArrayList<>();
        for (final Diagnostic<? extends JavaFileObject> d : diagCollector.getDiagnostics()) {
            final long rawLine = d.getLineNumber();
            if (rawLine == Diagnostic.NOPOS) {
                continue; // file-level diagnostic without position – skip
            }
            final int adjustedLine = (int)(rawLine - lineOffset);
            if (adjustedLine < 1) {
                LOGGER.warn("Diagnostic in system imports preamble (likely missing library): " + d.getMessage(Locale.US));
                continue;
            }

            final int startCol = Math.max(1, (int)d.getColumnNumber());

            // Compute proper end line and column from absolute character offsets
            final long startPos = d.getStartPosition();
            final long endPos = d.getEndPosition();
            int endLine = adjustedLine;
            int endCol = startCol + 1; // default single-char
            if (startPos != Diagnostic.NOPOS && endPos != Diagnostic.NOPOS && endPos > startPos) {
                final int rawEndLine = findLine(lineStarts, (int)endPos);
                endLine = rawEndLine - lineOffset;
                endCol = (int)(endPos - lineStarts[rawEndLine - 1]) + 1; // 1-based column
                if (endLine < adjustedLine) {
                    endLine = adjustedLine; // safety
                }
                if (endLine == adjustedLine && endCol <= startCol) {
                    endCol = startCol + 1; // safety
                }
            }

            final String severity = switch (d.getKind()) {
                case ERROR -> "Error";
                case WARNING, MANDATORY_WARNING -> "Warning";
                default -> "Info";
            };

            result.add(new DiagnosticItem(adjustedLine, startCol, endLine, endCol, severity,
                d.getMessage(Locale.US)));
        }
        return result;
    }

    // ─── Classpath ──────────────────────────────────────────────────────────

    /**
     * Returns the compile-time classpath string. The result is derived once from a temporary {@link JavaSnippet}
     * instance and then cached. {@link JavaSnippet} caches the underlying snippet jar statically, so this is cheap
     * after the first invocation.
     * <p>
     * <b>Known limitation:</b> only the default runtime class path (the generated snippet jar and its transitive
     * dependencies) is included here. User-added JARs or additional bundles configured in the node settings are
     * not available at this point because we have no {@link JavaSnippet} instance with applied settings. This may
     * cause false-positive "type not found" diagnostics for user-added libraries; a future improvement could inject
     * the model's additional classpath entries.
     */
    static String getClasspath() throws IOException {
        final String cached = CLASSPATH_CACHE.get();
        if (cached != null) {
            return cached;
        }
        // A fresh JavaSnippet (no settings) returns only the cached jSnippetJar.
        final File[] classpaths = new JavaSnippet().getRuntimeClassPath();
        final String cp = Arrays.stream(classpaths)
            .map(File::getAbsolutePath)
            .map(FilenameUtils::normalize)
            .collect(Collectors.joining(File.pathSeparator));
        CLASSPATH_CACHE.compareAndSet(null, cp);
        return CLASSPATH_CACHE.get();
    }

    // ─── Line-position helpers ───────────────────────────────────────────────

    /**
     * Returns an array where {@code lineStarts[i]} is the character offset of the start of line {@code i + 1}
     * (0-indexed array, 1-based line numbers). {@code lineStarts[0] == 0} because line 1 starts at offset 0.
     */
    private static int[] computeLineStarts(final String source) {
        final List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Returns the 1-based line number of the given character {@code offset} within the {@code lineStarts} array
     * as computed by {@link #computeLineStarts}.
     */
    private static int findLine(final int[] lineStarts, final int offset) {
        int lo = 0;
        int hi = lineStarts.length - 1;
        while (lo < hi) {
            final int mid = (lo + hi + 1) / 2;
            if (lineStarts[mid] <= offset) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo + 1; // 1-based
    }

    // ─── Inner classes ───────────────────────────────────────────────────────

    /**
     * A {@link SimpleJavaFileObject} that serves Java source code from an in-memory string, avoiding the need to
     * write source code to the file system.
     */
    private static final class InMemoryJavaSource extends SimpleJavaFileObject {

        private final String code;

        InMemoryJavaSource(final String className, final String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
