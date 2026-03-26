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
 *   Mar 26, 2026 (Carsten Haubold): created
 */
package org.knime.base.node.jsnippet;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.knime.core.node.NodeLogger;
import org.knime.core.webui.node.dialog.scripting.DiagnosticItem;
import org.knime.core.webui.node.dialog.scripting.DynamicCompletionItem;
import org.knime.core.webui.node.dialog.scripting.DynamicCompletionRequest;
import org.knime.core.webui.node.dialog.scripting.WorkflowControl;
import org.knime.core.webui.node.dialog.scripting.lsp.InProcessLanguageServer;
import org.knime.core.webui.node.dialog.scripting.lsp.LspTypes;
import org.knime.core.webui.node.dialog.scripting.lsp.LspTypes.CompletionContext;
import org.knime.core.webui.node.dialog.scripting.lsp.LspTypes.CompletionItem;
import org.knime.core.webui.node.dialog.scripting.lsp.LspTypes.CompletionItemKind;
import org.knime.core.webui.node.dialog.scripting.lsp.LspTypes.CompletionList;
import org.knime.core.webui.node.dialog.scripting.lsp.LspTypes.CompletionOptions;
import org.knime.core.webui.node.dialog.scripting.lsp.LspTypes.DiagnosticSeverity;
import org.knime.core.webui.node.dialog.scripting.lsp.LspTypes.InsertTextFormat;
import org.knime.core.webui.node.dialog.scripting.lsp.LspTypes.Position;
import org.knime.core.webui.node.dialog.scripting.lsp.LspTypes.Range;
import org.knime.core.webui.node.dialog.scripting.lsp.LspTypes.ServerCapabilities;

/**
 * Language server for the Java Snippet node. Provides auto-completion and diagnostics using the Language Server
 * Protocol over the in-process adapter.
 * <p>
 * Completion is delegated to {@link JavaSnippetCompletionService} and diagnostics (on-the-fly Java compilation) are
 * delegated to {@link JavaSnippetDiagnosticsService}. The latest field mappings (column/flow-variable ↔ Java field
 * bindings) are read on every request via the provided {@code Supplier<FieldMappings>} so that changes pushed by the
 * frontend are always reflected without recreating the server.
 *
 * @author Carsten Haubold, KNIME GmbH, Konstanz, Germany
 * @since 5.11
 */
@SuppressWarnings("restriction")
final class JavaSnippetLanguageServer extends InProcessLanguageServer {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(JavaSnippetLanguageServer.class);

    private final WorkflowControl m_workflowControl;

    private final Supplier<JavaSnippetScriptingService.FieldMappings> m_fieldMappingsSupplier;

    private final JavaSnippetDiagnosticsService m_diagnosticsService = new JavaSnippetDiagnosticsService();

    private static final long DIAGNOSTICS_DELAY_MS = 500;

    private final ScheduledExecutorService m_diagnosticsExecutor =
        Executors.newSingleThreadScheduledExecutor();

    private final AtomicLong m_diagnosticsGeneration = new AtomicLong(0);

    private final AtomicReference<ScheduledFuture<?>> m_pendingDiagnostics = new AtomicReference<>();

    /**
     * Creates a new language server for the Java Snippet node.
     *
     * @param workflowControl the workflow control providing access to input specs and flow variables
     * @param fieldMappingsSupplier supplier for the current field mappings (column/variable ↔ Java field); called on
     *            every completion request so that updates made via {@code preSuggestCodeHook} are reflected immediately
     */
    JavaSnippetLanguageServer(final WorkflowControl workflowControl,
            final Supplier<JavaSnippetScriptingService.FieldMappings> fieldMappingsSupplier) {
        m_workflowControl = workflowControl;
        m_fieldMappingsSupplier = fieldMappingsSupplier;
    }

    // -------------------------------------------------------------------------
    // Server capabilities
    // -------------------------------------------------------------------------

    @Override
    protected ServerCapabilities getServerCapabilities() {
        return new ServerCapabilities(
            new CompletionOptions(List.of("."), false), // dot-trigger; no resolve
            false, // no hover
            null // no signature help
        );
    }

    // -------------------------------------------------------------------------
    // Completion
    // -------------------------------------------------------------------------

    @Override
    protected CompletionList handleCompletion(final String uri, final Position position,
            final CompletionContext context) {
        final String text = getDocumentText(uri);
        if (text == null) {
            return new CompletionList(false, List.of());
        }

        // LSP uses 0-based line/character; DynamicCompletionRequest uses 1-based line/column.
        final var request = new DynamicCompletionRequest(
            text,
            position.line() + 1,
            position.character() + 1,
            mapTriggerKind(context.triggerKind()),
            context.triggerCharacter());

        try {
            final var completionService =
                new JavaSnippetCompletionService(m_workflowControl, m_fieldMappingsSupplier.get());
            final List<DynamicCompletionItem> items = completionService.getCompletions(request);
            final List<CompletionItem> lspItems = items.stream().map(JavaSnippetLanguageServer::toLspCompletionItem)
                .toList();
            return new CompletionList(false, lspItems);
        } catch (Exception e) { // NOSONAR – graceful degradation: empty list beats crashing
            LOGGER.warn("Completion request failed", e);
            return new CompletionList(false, List.of());
        }
    }

    private static CompletionItem toLspCompletionItem(final DynamicCompletionItem item) {
        final int format =
            item.insertTextIsSnippet() ? InsertTextFormat.SNIPPET : InsertTextFormat.PLAIN_TEXT;
        return new CompletionItem(
            item.label(),
            mapCompletionKind(item.kind()),
            item.detail(),
            item.documentation(),
            item.insertText(),
            format,
            item.sortText(),
            item.filterText(),
            null // no data for resolve
        );
    }

    private static int mapCompletionKind(final String kind) {
        if (kind == null) {
            return CompletionItemKind.TEXT;
        }
        return switch (kind) {
            case "Method" -> CompletionItemKind.METHOD;
            case "Field" -> CompletionItemKind.FIELD;
            case "Class" -> CompletionItemKind.CLASS;
            case "Keyword" -> CompletionItemKind.KEYWORD;
            case "Variable" -> CompletionItemKind.VARIABLE;
            case "Constant" -> CompletionItemKind.CONSTANT;
            case "Snippet" -> CompletionItemKind.SNIPPET;
            case "Property" -> CompletionItemKind.PROPERTY;
            default -> CompletionItemKind.TEXT;
        };
    }

    /**
     * Maps an LSP completion trigger kind integer to the string expected by {@link DynamicCompletionRequest}.
     *
     * @param triggerKind 1 = Invoked, 2 = TriggerCharacter, 3 = TriggerForIncompleteCompletions
     */
    private static String mapTriggerKind(final int triggerKind) {
        return triggerKind == 2 ? "TriggerCharacter" : "Invoked";
    }

    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    @Override
    protected void handleDidChange(final String uri, final int version, final String fullText) {
        scheduleDiagnostics(uri);
    }

    @Override
    protected void handleDidOpen(final String uri, final String languageId, final int version, final String text) {
        scheduleDiagnostics(uri);
    }

    /**
     * Debounces diagnostics computation: cancels any pending job and schedules a new one after
     * {@value #DIAGNOSTICS_DELAY_MS} ms. Reading the document text at execution time (not scheduling time) ensures
     * that rapid edits only produce one compilation run for the final state.
     * <p>
     * A monotonic generation counter prevents a slow job from publishing stale diagnostics over a newer result. The
     * pending-future reference is swapped atomically to avoid a check-then-act race between cancel and schedule.
     */
    private void scheduleDiagnostics(final String uri) {
        final long generation = m_diagnosticsGeneration.incrementAndGet();
        final ScheduledFuture<?> newFuture;
        try {
            newFuture = m_diagnosticsExecutor.schedule(() -> {
                if (m_diagnosticsGeneration.get() != generation) {
                    return; // newer generation scheduled, skip stale result
                }
                final String latestText = getDocumentText(uri);
                if (latestText != null) {
                    pushDiagnosticsImpl(uri, latestText);
                }
            }, DIAGNOSTICS_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) { // NOSONAR – executor shut down during close(), nothing to do
            return;
        }
        final ScheduledFuture<?> old = m_pendingDiagnostics.getAndSet(newFuture);
        if (old != null) {
            old.cancel(false);
        }
    }

    /**
     * Compiles the given document text and publishes diagnostics to the client.
     */
    private void pushDiagnosticsImpl(final String uri, final String text) {
        try {
            // DynamicCompletionRequest is reused here with line/column=1 as the diagnostics service
            // only uses the text field and ignores cursor position.
            final var request = new DynamicCompletionRequest(text, 1, 1, "Invoked", null);
            final List<DiagnosticItem> diagnostics = m_diagnosticsService.getDiagnostics(request);
            final List<LspTypes.Diagnostic> lspDiagnostics =
                diagnostics.stream().map(JavaSnippetLanguageServer::toLspDiagnostic).toList();
            publishDiagnostics(uri, lspDiagnostics);
        } catch (Exception e) { // NOSONAR – never crash the document change handler
            LOGGER.warn("Failed to compute diagnostics for Java Snippet editor", e);
            publishDiagnostics(uri, List.of());
        }
    }

    @Override
    public void close() {
        m_diagnosticsExecutor.shutdownNow();
        try {
            m_diagnosticsExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        super.close();
    }

    private static LspTypes.Diagnostic toLspDiagnostic(final DiagnosticItem item) {
        // DiagnosticItem uses 1-based lines and 1-based exclusive-end columns.
        // LspTypes.Position is 0-based; Range.end is exclusive – same convention, only base differs.
        final var start = new Position(item.startLine() - 1, item.startColumn() - 1);
        final var end = new Position(item.endLine() - 1, item.endColumn() - 1);
        return new LspTypes.Diagnostic(new Range(start, end), mapDiagnosticSeverity(item.severity()),
            item.message(), "JavaSnippet");
    }

    private static int mapDiagnosticSeverity(final String severity) {
        if (severity == null) {
            return DiagnosticSeverity.ERROR;
        }
        return switch (severity) {
            case "Error" -> DiagnosticSeverity.ERROR;
            case "Warning" -> DiagnosticSeverity.WARNING;
            case "Info" -> DiagnosticSeverity.INFORMATION;
            case "Hint" -> DiagnosticSeverity.HINT;
            default -> DiagnosticSeverity.ERROR;
        };
    }
}
