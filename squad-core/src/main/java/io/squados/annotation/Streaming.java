package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Enables token-by-token streaming for this agent.
 *
 * writer    — TokenWriter implementation class (default: StdoutTokenWriter)
 * chunkSize — Buffer N tokens before flushing to writer (default: 1 = immediate)
 *
 * Usage:
 * <pre>
 *   @Agent(role = AgentRole.WRITER, name = "StreamWriter")
 *   @Streaming(writer = StdoutTokenWriter.class, chunkSize = 5)
 *   public class StreamingAgent {}
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Streaming {
    Class<?> writer()    default io.squados.llm.StdoutTokenWriter.class;
    int      chunkSize() default 1;
}
