/*
 * ------------------------------------------------------------------------
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
 * ------------------------------------------------------------------------
 *
 * History
 *   Mar 26, 2026 (chaubold): created
 */
package org.knime.base.node.jsnippet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.webui.node.dialog.scripting.WorkflowControl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for {@link JavaSnippetLanguageServer}.
 * <p>
 * Exercises the full JSON-RPC → dispatch → response chain by calling {@code sendMessage()} with JSON-RPC strings and
 * capturing responses via {@code setMessageListener}. This validates position mapping (1-based to 0-based),
 * completion-kind mapping, and the diagnostics publish flow including debouncing.
 *
 * @author Carsten Haubold, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
public class JavaSnippetLanguageServerTest {

    // ── Constants ──────────────────────────────────────────────────────────

    private static final String DOC_URI = "file:///test.java";

    /** The debounce delay declared in JavaSnippetLanguageServer (ms). */
    private static final long DIAGNOSTICS_DELAY_MS = 500;

    /** Extra margin added on top of DIAGNOSTICS_DELAY_MS when waiting in tests. */
    private static final long DIAGNOSTICS_WAIT_MARGIN_MS = 400;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Fixtures ───────────────────────────────────────────────────────────

    private WorkflowControl m_workflowControl;

    private JavaSnippetLanguageServer m_server;

    private List<String> m_receivedMessages;

    // ── Setup / teardown ───────────────────────────────────────────────────

    @Before
    public void setUp() {
        m_workflowControl = mock(WorkflowControl.class);
        when(m_workflowControl.getInputSpec()).thenReturn(new PortObjectSpec[0]);
        m_server = new JavaSnippetLanguageServer(m_workflowControl, JavaSnippetLanguageServerTest::emptyMappings);
        m_receivedMessages = Collections.synchronizedList(new ArrayList<>());
        m_server.setMessageListener(m_receivedMessages::add);
    }

    @After
    public void tearDown() {
        m_server.close();
    }

    // ── Test 1: initialize capabilities ───────────────────────────────────

    /**
     * {@code initialize} must advertise a {@code completionProvider} with {@code "."} as the only trigger character.
     * Hover and signature-help must NOT be advertised by this server.
     */
    @Test
    public void testInitializeReturnsCompletionCapabilities() throws Exception {
        m_server.sendMessage(initializeRequest(1));

        var response = waitForResponseWithId(1);
        assertNotNull("Expected initialize response", response);
        var caps = response.path("result").path("capabilities");

        assertFalse("completionProvider should be present",
            caps.path("completionProvider").isMissingNode());
        var triggers = caps.path("completionProvider").path("triggerCharacters");
        assertEquals("Should have exactly one trigger character", 1, triggers.size());
        assertEquals("Trigger character should be '.'", ".", triggers.get(0).asText());

        assertFalse("hoverProvider should be present in response",
            caps.path("hoverProvider").isMissingNode());
        assertEquals("hoverProvider should be false", false, caps.path("hoverProvider").asBoolean(true));

        assertTrue("signatureHelpProvider should not be present",
            caps.path("signatureHelpProvider").isMissingNode());
    }

    // ── Test 2: completion returns items ──────────────────────────────────

    /**
     * A {@code textDocument/completion} request after {@code didOpen} must return at least one item.
     * Every item must carry a positive integer {@code kind} field (LSP CompletionItemKind).
     */
    @Test
    public void testCompletionRequestReturnsItems() throws Exception {
        sendDidOpen(validSnippetCode(), 1);
        sendCompletionRequest(2, 0, 0, 1, null);

        var response = waitForResponseWithId(2);
        assertNotNull("Expected completion response", response);
        var items = response.path("result").path("items");
        assertFalse("items should be present in result", items.isMissingNode());
        assertTrue("Should have at least one completion item", items.size() > 0);

        for (var item : items) {
            assertTrue("Every item must have a 'kind' field", item.has("kind"));
            assertTrue("kind must be a positive integer (LSP CompletionItemKind)",
                item.path("kind").asInt(-1) > 0);
        }
    }

    // ── Test 3: kind mapping ───────────────────────────────────────────────

    /**
     * The kind strings produced by {@link JavaSnippetCompletionService} must be translated to the correct LSP
     * {@code CompletionItemKind} integer values:
     * <ul>
     *   <li>"Keyword" → 14</li>
     *   <li>"Field"   → 5</li>
     *   <li>"Method"  → 2</li>
     * </ul>
     */
    @Test
    public void testCompletionKindMapping() throws Exception {
        sendDidOpen(validSnippetCode(), 1);
        sendCompletionRequest(2, 0, 0, 1, null);

        var response = waitForResponseWithId(2);
        var items = response.path("result").path("items");

        // "if" is always returned with kind Keyword → LSP kind 14
        var ifItem = findItemByLabel(items, "if");
        assertNotNull("Expected 'if' keyword item in completion list", ifItem);
        assertEquals("Keyword 'if' must map to LSP CompletionItemKind.KEYWORD (14)",
            14, ifItem.path("kind").asInt());

        // "ROWID" is always returned with kind Field → LSP kind 5
        var rowIdItem = findItemByLabel(items, "ROWID");
        assertNotNull("Expected 'ROWID' field item in completion list", rowIdItem);
        assertEquals("Field 'ROWID' must map to LSP CompletionItemKind.FIELD (5)",
            5, rowIdItem.path("kind").asInt());

        // "getCell" is an AbstractJSnippet method → kind Method → LSP kind 2
        var getCellItem = findItemByFilterText(items, "getCell");
        assertNotNull("Expected 'getCell' method item in completion list", getCellItem);
        assertEquals("Method 'getCell' must map to LSP CompletionItemKind.METHOD (2)",
            2, getCellItem.path("kind").asInt());
    }

    // ── Test 4: trigger character mapping ─────────────────────────────────

    /**
     * A completion request with {@code triggerKind=2} (TriggerCharacter) and {@code triggerCharacter="."} must be
     * accepted without error and return items. This verifies that the trigger-kind integer is correctly mapped to the
     * string expected by {@link JavaSnippetCompletionService}.
     */
    @Test
    public void testCompletionTriggerCharacterMapping() throws Exception {
        sendDidOpen(validSnippetCode(), 1);
        // triggerKind=2 means TriggerCharacter; triggerCharacter="."
        sendCompletionRequest(2, 0, 0, 2, ".");

        var response = waitForResponseWithId(2);
        assertNotNull("Expected completion response for dot-triggered request", response);

        // The response must be a valid CompletionList – no error node
        assertFalse("Response must not contain an 'error' field",
            response.has("error") && !response.path("error").isNull());
        var items = response.path("result").path("items");
        assertFalse("items array must be present", items.isMissingNode());
        // Even if P1 dot-resolution falls back to P0, we still get items
        assertTrue("Dot-triggered completion should return at least one item", items.size() > 0);
    }

    // ── Test 5: diagnostics pushed after didOpen ──────────────────────────

    /**
     * After {@code textDocument/didOpen} with syntactically invalid Java code, a
     * {@code textDocument/publishDiagnostics} notification must be received (after the debounce period). The
     * diagnostic positions must use 0-based line/character coordinates.
     */
    @Test
    public void testDiagnosticsPushedAfterDidOpen() throws Exception {
        sendDidOpen(invalidSnippetCode(), 1);

        var diagNotifications = waitForDiagnosticsNotifications(1);
        assertFalse("Expected at least one publishDiagnostics notification", diagNotifications.isEmpty());

        // Validate the first diagnostics notification
        var notification = diagNotifications.get(0);
        assertEquals("Notification URI must match opened document",
            DOC_URI, notification.path("params").path("uri").asText());
        var diagnostics = notification.path("params").path("diagnostics");
        assertFalse("Invalid code must produce at least one diagnostic", diagnostics.isEmpty());

        // Positions must be 0-based (line >= 0, character >= 0)
        for (var diag : diagnostics) {
            int startLine = diag.path("range").path("start").path("line").asInt(-1);
            int startChar = diag.path("range").path("start").path("character").asInt(-1);
            assertTrue("Diagnostic start.line must be 0-based (>= 0), was " + startLine, startLine >= 0);
            assertTrue("Diagnostic start.character must be 0-based (>= 0), was " + startChar, startChar >= 0);
        }
    }

    // ── Test 6: diagnostics are debounced ─────────────────────────────────

    /**
     * When multiple {@code textDocument/didChange} notifications arrive in rapid succession, the server must publish
     * only ONE {@code textDocument/publishDiagnostics} notification reflecting the LAST document state.
     */
    @Test
    public void testDiagnosticsDebounceOnRapidChanges() throws Exception {
        final int changeCount = 5;

        // Send first change with valid code
        sendDidChange(DOC_URI, 1, validSnippetCode());

        // Rapid successive changes – all within a few ms, well below the 500 ms debounce window
        for (int i = 2; i <= changeCount; i++) {
            // Alternate between valid and invalid code; final state is invalid
            String code = (i % 2 == 0) ? validSnippetCode() : invalidSnippetCode();
            sendDidChange(DOC_URI, i, code);
        }

        // Poll until at least 1 diagnostics notification arrives
        var diagNotifications = waitForDiagnosticsNotifications(1);
        assertFalse("Expected at least one publishDiagnostics notification after debounce",
            diagNotifications.isEmpty());

        // Wait an additional short period to confirm no MORE notifications arrive
        Thread.sleep(1000);
        diagNotifications = findDiagnosticsNotifications();
        assertEquals("Exactly one publishDiagnostics notification expected after debounce",
            1, diagNotifications.size());

        // The single notification must reflect the LAST document state (code at version changeCount)
        // Version changeCount is odd → invalidSnippetCode → non-empty diagnostics
        var diagnostics = diagNotifications.get(0).path("params").path("diagnostics");
        assertFalse("Diagnostics must reflect the last (invalid) document state and be non-empty",
            diagnostics.isEmpty());
    }

    // ── Test 7: close shuts down cleanly ──────────────────────────────────

    /**
     * After {@link JavaSnippetLanguageServer#close()} returns, no further
     * {@code textDocument/publishDiagnostics} notifications must be delivered to the listener.
     */
    @Test
    public void testCloseShutsDownCleanly() throws Exception {
        sendDidOpen(invalidSnippetCode(), 1);

        // close() blocks until the executor has terminated (up to 2 s)
        m_server.close();

        int countAfterClose = m_receivedMessages.size();

        // Wait another full debounce period to confirm silence
        Thread.sleep(DIAGNOSTICS_DELAY_MS + DIAGNOSTICS_WAIT_MARGIN_MS);

        assertEquals("No messages should arrive after close() returns",
            countAfterClose, m_receivedMessages.size());
    }

    // ── JSON-RPC message helpers ───────────────────────────────────────────

    private void sendDidOpen(final String text, final int version) {
        m_server.sendMessage(
            "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{" +
            "\"uri\":\"" + DOC_URI + "\"," +
            "\"languageId\":\"java\"," +
            "\"version\":" + version + "," +
            "\"text\":" + jsonString(text) + "}}}");
    }

    private void sendDidChange(final String uri, final int version, final String fullText) {
        m_server.sendMessage(
            "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didChange\",\"params\":{\"textDocument\":{" +
            "\"uri\":\"" + uri + "\"," +
            "\"version\":" + version + "}," +
            "\"contentChanges\":[{\"text\":" + jsonString(fullText) + "}]}}");
    }

    private void sendCompletionRequest(final int id, final int line, final int character, final int triggerKind,
            final String triggerCharacter) {
        String triggerCharJson = triggerCharacter != null
            ? "\"" + triggerCharacter + "\""
            : "null";
        m_server.sendMessage(
            "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"textDocument/completion\",\"params\":{" +
            "\"textDocument\":{\"uri\":\"" + DOC_URI + "\"}," +
            "\"position\":{\"line\":" + line + ",\"character\":" + character + "}," +
            "\"context\":{\"triggerKind\":" + triggerKind + ",\"triggerCharacter\":" + triggerCharJson + "}}}");
    }

    private static String initializeRequest(final int id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"initialize\",\"params\":{\"capabilities\":{}}}";
    }

    // ── Response / notification helpers ───────────────────────────────────

    /**
     * Polls up to 5 seconds for a response message with the given numeric {@code id}.
     *
     * @param id the JSON-RPC request id
     * @return the parsed response node (never {@code null})
     * @throws AssertionError if the response does not arrive within the timeout
     */
    private JsonNode waitForResponseWithId(final int id) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            synchronized (m_receivedMessages) {
                for (String msg : m_receivedMessages) {
                    JsonNode node = MAPPER.readTree(msg);
                    if (node.has("id") && node.get("id").asInt() == id) {
                        return node;
                    }
                }
            }
            Thread.sleep(50);
        }
        fail("Timed out waiting for response with id " + id);
        return null; // unreachable
    }

    /**
     * Returns all parsed {@code textDocument/publishDiagnostics} notification nodes received so far.
     */
    private List<JsonNode> findDiagnosticsNotifications() throws Exception {
        List<JsonNode> result = new ArrayList<>();
        synchronized (m_receivedMessages) {
            for (String msg : m_receivedMessages) {
                JsonNode node = MAPPER.readTree(msg);
                if (node.has("method") && "textDocument/publishDiagnostics".equals(node.get("method").asText())) {
                    result.add(node);
                }
            }
        }
        return result;
    }

    /**
     * Polls until at least {@code expectedCount} {@code textDocument/publishDiagnostics} notifications have arrived,
     * or the 5-second timeout expires.
     *
     * @param expectedCount minimum number of notifications to wait for
     * @return the list of diagnostics notifications found so far
     */
    private List<JsonNode> waitForDiagnosticsNotifications(final int expectedCount) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        List<JsonNode> found;
        while (true) {
            found = findDiagnosticsNotifications();
            if (found.size() >= expectedCount || System.currentTimeMillis() >= deadline) {
                break;
            }
            Thread.sleep(50);
        }
        return found;
    }

    /**
     * Finds an item node by its {@code label} field, or returns {@code null}.
     */
    private static JsonNode findItemByLabel(final JsonNode items, final String label) {
        return StreamSupport.stream(items.spliterator(), false)
            .filter(item -> label.equals(item.path("label").asText(null)))
            .findFirst()
            .orElse(null);
    }

    /**
     * Finds an item node by its {@code filterText} field, or returns {@code null}.
     */
    private static JsonNode findItemByFilterText(final JsonNode items, final String filterText) {
        return StreamSupport.stream(items.spliterator(), false)
            .filter(item -> filterText.equals(item.path("filterText").asText(null)))
            .findFirst()
            .orElse(null);
    }

    // ── Code fixtures ──────────────────────────────────────────────────────

    /**
     * A syntactically valid Java Snippet editor text with an empty snippet body.
     */
    private static String validSnippetCode() {
        return "\n\npublic class JSnippet extends AbstractJSnippet {\n"
            + "\n\n  @Override\n"
            + "  public void snippet() throws TypeException, ColumnException, Abort {\n"
            + "    \n"
            + "  }\n}\n";
    }

    /**
     * A syntactically invalid Java Snippet editor text (missing semicolon in body).
     */
    private static String invalidSnippetCode() {
        return "\n\npublic class JSnippet extends AbstractJSnippet {\n"
            + "\n\n  @Override\n"
            + "  public void snippet() throws TypeException, ColumnException, Abort {\n"
            + "    int x = 5\n" // missing semicolon – compile error
            + "  }\n}\n";
    }

    private static JavaSnippetScriptingService.FieldMappings emptyMappings() {
        return new JavaSnippetScriptingService.FieldMappings(
            new JavaSnippetScriptingService.FieldMapping[0],
            new JavaSnippetScriptingService.FieldMapping[0],
            new JavaSnippetScriptingService.FieldMapping[0],
            new JavaSnippetScriptingService.FieldMapping[0]);
    }

    /**
     * Converts a Java string to a JSON string literal (escapes backslashes and double quotes only; newlines handled
     * via JSON unicode escapes).
     */
    private static String jsonString(final String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
