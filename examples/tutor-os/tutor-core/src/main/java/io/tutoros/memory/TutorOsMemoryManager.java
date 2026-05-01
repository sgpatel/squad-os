package io.tutoros.memory;

import io.squados.memory.MemoryManager;
import io.squados.memory.annotation.Memory;
import io.squados.memory.retrieval.EmbeddingPort;
import io.squados.memory.retrieval.MemoryRouter;
import io.squados.memory.store.MemoryRecord;

import java.util.Collections;
import java.util.List;

/**
 * TutorOS-flavoured {@link MemoryManager} that fixes two real-world
 * problems with the framework's vanilla read path:
 *
 *   1. {@code AgentWrapper} calls {@code memoryManager.read(ann, name,
 *      null, null, query)} — both squadId and sessionId are null. The
 *      in-process store filters records with {@code r.getSquadId().equals(squadId)},
 *      which is always false against the non-null squadId we wrote with.
 *      Net effect on stock SquadOS today: every read returns nothing.
 *      We fix it by substituting the real squadId from the per-turn
 *      {@link TurnContext} before delegating to {@code super.read}.
 *
 *   2. Cross-subject memory pollution. A learner studying biology and
 *      then math will, with stock cosine retrieval, see biology memories
 *      leak into a math turn whenever the embedding similarity exceeds
 *      {@code minScore}. We post-filter retrieved records by the active
 *      {@code subject:} tag (and, when present, the {@code topic:} tag)
 *      so that recall is hard-scoped to the subject the learner is
 *      currently studying.
 *
 * The TutoringPipeline owns the lifecycle: it sets the {@link ThreadLocal}
 * before invoking agents and clears it at the end of every turn (try/finally).
 * Outside a managed turn the manager behaves exactly like the parent class
 * with the original null arguments — i.e. tests and CLI flows are unaffected.
 *
 * Why a subclass not a fork: the framework {@code MemoryManager} is the
 * stable injection target {@code SquadContext} pulls via ObjectProvider.
 * Subclassing keeps us behind the same surface, so a future framework PR
 * (auto-fill squadId from context, accept tag filters) can replace this
 * with a one-line {@code @Bean MemoryManager} and a deletion of this file.
 */
public class TutorOsMemoryManager extends MemoryManager {

    /**
     * Per-turn context. The pipeline wraps each {@code process()} call in a
     * try/finally that sets and clears this; everything outside that scope
     * sees null and falls back to the parent behaviour.
     */
    public static final ThreadLocal<TurnContext> CTX = new ThreadLocal<>();

    public TutorOsMemoryManager(MemoryRouter router, EmbeddingPort embedder) {
        super(router, embedder);
    }

    @Override
    public List<MemoryRecord> read(Memory memAnn,
                                   String agentId, String squadId,
                                   String sessionId, String queryText) {
        TurnContext ctx = CTX.get();

        // Substitute squadId / sessionId when the caller passed null but a
        // turn context is active. This is the AgentWrapper case — fixing it
        // makes "## Relevant Memories" actually start showing up in prompts.
        String effectiveSquad   = (squadId   == null && ctx != null) ? ctx.squadId()   : squadId;
        String effectiveSession = (sessionId == null && ctx != null) ? ctx.sessionId() : sessionId;

        List<MemoryRecord> raw;
        try {
            raw = super.read(memAnn, agentId, effectiveSquad, effectiveSession, queryText);
        } catch (RuntimeException e) {
            // Memory must never break execution. Mirror the framework
            // contract from AgentWrapper.retrieveMemories.
            return Collections.emptyList();
        }

        // No active context → no subject/topic filter. Return everything.
        if (ctx == null || (ctx.subject() == null && ctx.topic() == null)) {
            return raw;
        }

        // Subject-first hard filter: drop any record that doesn't carry
        // the active subject tag. A record with NO subject tag is treated
        // as "legacy / shared" and let through, otherwise old un-tagged
        // memories would silently disappear after this change ships.
        String subjectTag = ctx.subjectTag();
        String topicTag   = ctx.topicTag();

        return raw.stream()
            .filter(r -> matchesSubjectFilter(r, subjectTag))
            .filter(r -> matchesTopicFilter(r, topicTag))
            .toList();
    }

    /** Allow records that either match the active subject tag or have no subject tag. */
    private static boolean matchesSubjectFilter(MemoryRecord r, String subjectTag) {
        if (subjectTag == null) return true;
        boolean hasAnySubjectTag = false;
        for (String tag : safeTags(r)) {
            if (tag == null) continue;
            if (tag.equals(subjectTag)) return true;
            if (tag.startsWith("subject:")) hasAnySubjectTag = true;
        }
        // Untagged legacy records are passed through; tagged records that
        // don't match are excluded. This is the right default for a
        // forward-compatible rollout.
        return !hasAnySubjectTag;
    }

    /**
     * Topic filter is intentionally softer than subject: many tutor turns
     * span sub-topics within a single topic, and we don't want to block
     * recall when the labels drift. Records with a non-matching topic tag
     * are demoted, not removed — the underlying ranking already handles
     * relevance via cosine similarity.
     *
     * Today this method is a no-op pass-through; we keep it as the seam
     * for the topic-aware demotion in a follow-up PR.
     */
    private static boolean matchesTopicFilter(MemoryRecord r, String topicTag) {
        return true;
    }

    private static String[] safeTags(MemoryRecord r) {
        String[] tags = r.getTags();
        return tags != null ? tags : new String[0];
    }
}
