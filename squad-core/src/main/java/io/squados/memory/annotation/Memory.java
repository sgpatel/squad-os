package io.squados.memory.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches memory behaviour to an agent method.
 * The MemoryManager fires around annotated methods:
 *   BEFORE: retrieves top-k similar memories, injects into TaskContext
 *   AFTER:  embeds and stores the method return value as a new memory record
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Memory {
    MemoryType  type()       default MemoryType.WORKING;
    MemoryScope scope()      default MemoryScope.AGENT;
    MemoryOp    op()         default MemoryOp.READ_WRITE;
    int         topK()       default 3;
    float       minScore()   default 0.72f;
    String[]    tags()       default {};
    Importance  importance() default Importance.MEDIUM;
    boolean     promote()    default false;
}
