package io.tutoros.pipeline;

import io.squados.memory.annotation.Importance;
import io.squados.memory.annotation.Memory;
import io.squados.memory.annotation.MemoryOp;
import io.squados.memory.annotation.MemoryScope;
import io.squados.memory.annotation.MemoryType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;

/**
 * MemoryHelper — builds a synthetic {@link Memory} annotation instance for
 * programmatic writes through {@link io.squados.memory.MemoryManager#write}.
 *
 * Why this exists: {@code MemoryManager.write(...)} takes a {@code @Memory}
 * annotation as its first argument because the framework's primary write
 * path is method-level annotation processing (Phase 3 AOP — not yet
 * implemented). Until that ships, callers that want to write explicitly
 * have to hand it an annotation instance. We synthesise one via
 * {@link Proxy} rather than declaring a sentinel method just to read
 * its annotation off — clearer intent, no dead method, no reflection
 * lookup at every call site.
 *
 * When the framework gains a builder API or AOP write hook, this helper
 * becomes a one-line delete.
 */
final class MemoryHelper {

    private MemoryHelper() {}

    /**
     * Build a {@link Memory} annotation suitable for writing a single
     * end-of-turn record to EPISODIC memory at SQUAD scope.
     *
     * SQUAD scope means any agent in the squad can recall it next turn —
     * the tutor agents (Socratic, Direct, Diagnostic, Progress, …) all
     * share the same memory pool keyed by squadId. Per-agent isolation
     * would require the writer to know which agent will read, which we
     * don't until the next debate runs.
     */
    static Memory episodicSquadWrite(Importance importance, String[] tags) {
        return synthesize(
            MemoryType.EPISODIC,
            MemoryScope.SQUAD,
            MemoryOp.WRITE,
            importance != null ? importance : Importance.MEDIUM,
            tags != null ? tags : new String[0]
        );
    }

    private static Memory synthesize(MemoryType type, MemoryScope scope,
                                     MemoryOp op, Importance importance,
                                     String[] tags) {
        return (Memory) Proxy.newProxyInstance(
            Memory.class.getClassLoader(),
            new Class<?>[]{ Memory.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "type"           -> type;
                case "scope"          -> scope;
                case "op"             -> op;
                case "topK"           -> 3;
                case "minScore"       -> 0.72f;
                case "tags"           -> tags;
                case "importance"     -> importance;
                case "promote"        -> false;
                case "annotationType" -> Memory.class;
                case "toString"       -> "@Memory(synthetic, type=" + type + ", scope=" + scope + ")";
                case "hashCode"       -> System.identityHashCode(proxy);
                case "equals"         -> proxy == args[0];
                default -> {
                    // Defensive: any annotation method we forgot returns its default.
                    Object def = method.getDefaultValue();
                    if (def != null) yield def;
                    throw new UnsupportedOperationException("Unhandled @Memory method: " + method.getName());
                }
            }
        );
    }
}
