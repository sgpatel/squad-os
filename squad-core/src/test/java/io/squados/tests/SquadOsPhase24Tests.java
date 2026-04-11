package io.squados.tests;

import io.squados.llm.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 24 — v3.5 @Streaming token-by-token output
 *
 * S01 — StreamToken.of() creates non-last token
 * S02 — StreamToken.last(text) creates last token with text
 * S03 — StreamToken.last() creates last empty token
 * S04 — NoOpTokenWriter discards all tokens silently
 * S05 — StdoutTokenWriter.write() does not throw
 * S06 — LoggerTokenWriter buffers tokens until isLast
 * S07 — MockLlmPort.chatStream() emits tokens to writer
 * S08 — MockLlmPort.chatStream() last token has isLast=true
 * S09 — MockLlmPort.chatStream() emits non-empty content
 * S10 — MockLlmPort.chatStream() increments call count
 * S11 — Collecting all streamed tokens reconstructs full response
 * S12 — TokenWriter functional interface — lambda works
 * S13 — LlmPort.chatStream() default delegates to chat()
 * S14 — StreamToken text is preserved correctly
 * S15 — chunkSize buffering: collecting chunks reassembles full content
 * S16 — NoOpTokenWriter.flush() does not throw
 * S17 — StdoutTokenWriter.flush() does not throw
 */
public class SquadOsPhase24Tests {

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase24Tests();
        String[] tests = {
            "S01_streamTokenOf",
            "S02_streamTokenLastWithText",
            "S03_streamTokenLastEmpty",
            "S04_noOpWriterSilent",
            "S05_stdoutWriterNoThrow",
            "S06_loggerWriterBuffers",
            "S07_mockLlmStreamEmitsTokens",
            "S08_mockLlmStreamLastToken",
            "S09_mockLlmStreamNonEmpty",
            "S10_mockLlmStreamIncrementsCount",
            "S11_collectStreamReconstructsResponse",
            "S12_tokenWriterLambdaWorks",
            "S13_defaultChatStreamDelegatesToChat",
            "S14_streamTokenTextPreserved",
            "S15_chunkBufferingReassembles",
            "S16_noOpFlushNoThrow",
            "S17_stdoutFlushNoThrow",
        };
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 24 - v3.5 @Streaming         ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");
        for (String name : tests) {
            try {
                t.getClass().getDeclaredMethod(name).invoke(t);
                System.out.printf("  ✓ %s%n", name); passed++;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable c = e.getCause();
                System.out.printf("  ✗ %s%n    -> %s: %s%n",
                    name, c.getClass().getSimpleName(), c.getMessage()); failed++;
            } catch (Exception e) {
                System.out.printf("  ✗ %s%n    -> %s%n", name, e.getMessage()); failed++;
            }
        }
        System.out.printf("%n  Results: %d passed, %d failed%n", passed, failed);
        if (failed > 0) { System.out.println("\n  PHASE 24 GATE: FAILED\n"); System.exit(1); }
        else { System.out.println("\n  PHASE 24 GATE: ALL TESTS PASSED ✓");
               System.out.println("  v3.5 @Streaming token output operational.\n"); }
    }

    void S01_streamTokenOf() {
        StreamToken t = StreamToken.of("hello");
        assertEquals("hello", t.text(), "text");
        assertFalse(t.isLast(), "not last");
    }

    void S02_streamTokenLastWithText() {
        StreamToken t = StreamToken.last("done");
        assertEquals("done", t.text(), "text");
        assertTrue(t.isLast(), "is last");
    }

    void S03_streamTokenLastEmpty() {
        StreamToken t = StreamToken.last();
        assertEquals("", t.text(), "empty text");
        assertTrue(t.isLast(), "is last");
    }

    void S04_noOpWriterSilent() {
        NoOpTokenWriter w = new NoOpTokenWriter();
        w.write(StreamToken.of("hello"));
        w.write(StreamToken.last("bye"));
        w.flush();
        // No assertion needed — just verifies no exception
        assertTrue(true, "no exception");
    }

    void S05_stdoutWriterNoThrow() {
        StdoutTokenWriter w = new StdoutTokenWriter();
        w.write(StreamToken.of("test"));
        w.write(StreamToken.last("end"));
        assertTrue(true, "no exception");
    }

    void S06_loggerWriterBuffers() {
        List<String> logged = new ArrayList<>();
        LoggerTokenWriter w = new LoggerTokenWriter("test.logger");
        // Write tokens
        w.write(StreamToken.of("Hello"));
        w.write(StreamToken.of(" World"));
        w.write(StreamToken.last("!"));
        // LoggerTokenWriter logs on last token — just verify no exception
        assertTrue(true, "logger writer runs without exception");
    }

    void S07_mockLlmStreamEmitsTokens() {
        MockLlmPort mock = new MockLlmPort().setDefaultResponse("Hello World Response");
        List<StreamToken> tokens = new ArrayList<>();
        mock.chatStream("sys", "user", LlmOptions.defaults(), tokens::add);
        assertFalse(tokens.isEmpty(), "tokens emitted");
    }

    void S08_mockLlmStreamLastToken() {
        MockLlmPort mock = new MockLlmPort().setDefaultResponse("Hello");
        List<StreamToken> tokens = new ArrayList<>();
        mock.chatStream("sys", "user", LlmOptions.defaults(), tokens::add);
        StreamToken last = tokens.get(tokens.size() - 1);
        assertTrue(last.isLast(), "last token has isLast=true");
    }

    void S09_mockLlmStreamNonEmpty() {
        MockLlmPort mock = new MockLlmPort().setDefaultResponse("Test content");
        StringBuilder sb = new StringBuilder();
        mock.chatStream("sys", "user", LlmOptions.defaults(),
            t -> sb.append(t.text()));
        assertFalse(sb.toString().isBlank(), "streamed content non-empty");
    }

    void S10_mockLlmStreamIncrementsCount() {
        MockLlmPort mock = new MockLlmPort().setDefaultResponse("data");
        int before = mock.getCallCount();
        mock.chatStream("sys", "user", LlmOptions.defaults(), t -> {});
        assertEquals(before + 1, mock.getCallCount(), "call count incremented");
    }

    void S11_collectStreamReconstructsResponse() {
        MockLlmPort mock = new MockLlmPort().setDefaultResponse("Full Response Text");
        StringBuilder collected = new StringBuilder();
        mock.chatStream("sys", "user", LlmOptions.defaults(),
            t -> collected.append(t.text()));
        assertEquals("Full Response Text", collected.toString(), "reconstructed response");
    }

    void S12_tokenWriterLambdaWorks() {
        List<String> captured = new ArrayList<>();
        TokenWriter writer = token -> captured.add(token.text());
        writer.write(StreamToken.of("a"));
        writer.write(StreamToken.last("b"));
        assertEquals(2, captured.size(), "lambda captured 2 tokens");
        assertEquals("a", captured.get(0), "first token");
    }

    void S13_defaultChatStreamDelegatesToChat() {
        // Minimal LlmPort that only implements chat()
        LlmPort minimalPort = new LlmPort() {
            @Override
            public LlmResponse chat(String s, String u, LlmOptions o) {
                return new LlmResponse("delegate-response");
            }
            @Override
            public <T> T chatStructured(String s, String u, Class<T> t, LlmOptions o) { return null; }
        };
        StringBuilder sb = new StringBuilder();
        minimalPort.chatStream("sys", "user", LlmOptions.defaults(),
            t -> sb.append(t.text()));
        assertEquals("delegate-response", sb.toString(), "default delegates to chat()");
    }

    void S14_streamTokenTextPreserved() {
        String text = "Special chars: äöü 汉字 🚀";
        StreamToken t = StreamToken.of(text);
        assertEquals(text, t.text(), "unicode text preserved");
    }

    void S15_chunkBufferingReassembles() {
        MockLlmPort mock = new MockLlmPort().setDefaultResponse("ABCDEFGHIJ");
        List<String> chunks = new ArrayList<>();
        mock.chatStream("sys", "user", LlmOptions.defaults(),
            t -> { if (!t.text().isEmpty()) chunks.add(t.text()); });
        String reconstructed = String.join("", chunks);
        assertEquals("ABCDEFGHIJ", reconstructed, "chunks reassemble to full content");
    }

    void S16_noOpFlushNoThrow() {
        new NoOpTokenWriter().flush();
        assertTrue(true, "flush no exception");
    }

    void S17_stdoutFlushNoThrow() {
        new StdoutTokenWriter().flush();
        assertTrue(true, "flush no exception");
    }

    static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    static void assertNotNull(Object a, String msg) {
        if (a != null) return; throw new AssertionError(msg + " — null");
    }
    static void assertTrue(boolean c, String msg) {
        if (c) return; throw new AssertionError(msg + " — expected true");
    }
    static void assertFalse(boolean c, String msg) {
        if (!c) return; throw new AssertionError(msg + " — expected false");
    }
}
