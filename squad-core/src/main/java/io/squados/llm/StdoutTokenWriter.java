package io.squados.llm;

/**
 * Writes streaming tokens to System.out.
 * Default TokenWriter for @Streaming agents in dev/local mode.
 */
public class StdoutTokenWriter implements TokenWriter {

    @Override
    public void write(StreamToken token) {
        System.out.print(token.text());
        if (token.isLast()) System.out.println();
    }

    @Override
    public void flush() {
        System.out.flush();
    }
}
