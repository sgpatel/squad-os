package io.squados.llm;

/**
 * A single token chunk emitted during streaming LLM output.
 *
 * isLast() signals the final token — consumers can flush buffered content.
 */
public record StreamToken(
        String text,
        boolean isLast
) {
    public static StreamToken of(String text)   { return new StreamToken(text, false); }
    public static StreamToken last(String text) { return new StreamToken(text, true); }
    public static StreamToken last()            { return new StreamToken("", true); }
}
