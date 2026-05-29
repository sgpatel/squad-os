package io.tutoros.memory;

/**
 * Per-turn tutoring context — squad/session identity plus the active
 * subject and topic. Used to:
 *
 *   1. Inject a non-null {@code squadId} into the memory read path.
 *      AgentWrapper unconditionally passes {@code null} for squadId,
 *      which makes every saved record (which has a non-null squadId)
 *      fail the {@code equals(null)} filter inside the store. The
 *      pipeline wraps each turn in a TurnContext so the memory manager
 *      can substitute the real squadId before delegating to super.read.
 *
 *   2. Hard-isolate memory recall by subject. Without this, memories
 *      from "biology / photosynthesis" leak into a "math / trigonometry"
 *      turn whenever the cosine similarity is high enough — a known
 *      cross-subject pollution failure mode.
 *
 * Stored as a {@link ThreadLocal} on {@link TutorOsMemoryManager} so
 * AgentWrapper (framework code we don't fork) doesn't have to know
 * subject/topic exist.
 *
 * @param squadId    the squad name registered with SquadContext
 * @param subject    active subject (lowercased), e.g. "math" — null
 *                   means "no subject filter, return everything"
 * @param topic      active topic (lowercased), e.g. "trigonometry" —
 *                   null means "no topic filter"
 * @param sessionId  the active session id; written into memory tags
 */
public record TurnContext(
    String squadId,
    String subject,
    String topic,
    String sessionId
) {
    /** Build a context, lowercasing subject + topic for stable tag matching. */
    public static TurnContext of(String squadId, String subject, String topic, String sessionId) {
        return new TurnContext(
            squadId,
            subject != null && !subject.isBlank() ? subject.trim().toLowerCase() : null,
            topic   != null && !topic.isBlank()   ? topic.trim().toLowerCase()   : null,
            sessionId
        );
    }

    /** Tag string the memory layer matches against ({@code subject:math}). */
    public String subjectTag() {
        return subject == null ? null : "subject:" + subject;
    }

    /** Tag string for the topic ({@code topic:trigonometry}). */
    public String topicTag() {
        return topic == null ? null : "topic:" + topic;
    }
}
