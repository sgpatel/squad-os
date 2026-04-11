package io.squados.llm;

/**
 * Discards all streaming tokens.
 * Useful for tests and scenarios where streaming output is not needed.
 */
public class NoOpTokenWriter implements TokenWriter {

    @Override
    public void write(StreamToken token) {
        // intentionally empty
    }
}
