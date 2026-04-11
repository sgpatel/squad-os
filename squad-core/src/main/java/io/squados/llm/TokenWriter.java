package io.squados.llm;

/**
 * Sink for streaming tokens produced by @Streaming agents.
 *
 * Implement this interface to control where tokens are written:
 *   StdoutTokenWriter — System.out (default)
 *   LoggerTokenWriter — java.util.logging.Logger
 *   NoOpTokenWriter   — /dev/null (testing)
 */
@FunctionalInterface
public interface TokenWriter {

    /** Called for every chunk produced by the LLM. */
    void write(StreamToken token);

    /** Optional flush — called after isLast token. Default: no-op. */
    default void flush() {}
}
