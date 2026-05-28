package io.tutoros.book;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One chapter of an uploaded book. Carries enough text to drive
 * BookCoachAgent's six learning lenses (basic / intermediate / advanced
 * / real-life usage / history / future scope) WITHOUT re-reading the
 * full PDF on every learning request — the body is grounded source the
 * LLM cites against, capped at {@link #MAX_BODY_CHARS} so per-call
 * token cost stays bounded.
 *
 * <p>Concepts are extracted at ingest time (PR-1) and feed straight
 * into the M3 mastery graph in PR-2 — every chapter the learner studies
 * moves N rows in {@code tutor_mastery}, so the review queue stays
 * full automatically without the learner curating concept lists by hand.
 *
 * <p>Mutable public fields match {@link io.tutoros.model.LearnerProfile}
 * and {@link io.tutoros.mastery.ConceptMastery} — keeps serialisation
 * to JSON simple (Jackson works without any annotations) and the JDBC
 * store can mirror columns 1:1.
 */
public class Chapter {

    /**
     * Body cap. ~6k chars ≈ ~1.5k tokens at typical English. Enough to
     * ground BookCoach across all six lenses without blowing the LLM
     * context. Books with longer chapters get truncated at character
     * boundaries (preserving sentence start) by {@code BookExtractionService}.
     */
    public static final int MAX_BODY_CHARS = 6_000;

    /** 1-based chapter number, in document order. */
    public int number;

    /**
     * Human-readable chapter title. Either the line that triggered the
     * heading regex ("Chapter 3: Photosynthesis") or a generated
     * fallback ("Pages 60–88") for un-headed segments.
     */
    public String title;

    /**
     * First ~200 chars of the chapter body, used as a card subtitle
     * in the UI without sending the full body to the client.
     */
    public String summary;

    /** 1-based first PDF page this chapter starts on. */
    public int pageStart;
    /** 1-based last PDF page this chapter occupies (inclusive). */
    public int pageEnd;

    /** Chapter text, capped to {@link #MAX_BODY_CHARS}. */
    public String body;

    /**
     * Concept tags extracted at ingest. Each becomes a mastery row
     * when the learner first interacts with this chapter via BookCoach
     * (PR-2). Lower-cased + de-duped at ingest.
     */
    public List<String> concepts = new ArrayList<>();

    /** First N chars of the body, used as a UI subtitle/teaser. */
    public static String defaultSummary(String body) {
        if (body == null || body.isBlank()) return "";
        String trimmed = body.trim();
        if (trimmed.length() <= 200) return trimmed;
        // Cut at the nearest sentence break to keep it readable.
        int cut = Math.min(200, trimmed.length());
        for (int i = Math.min(199, trimmed.length() - 1); i > 100; i--) {
            char c = trimmed.charAt(i);
            if (c == '.' || c == '!' || c == '?') { cut = i + 1; break; }
        }
        return trimmed.substring(0, cut).trim() + "…";
    }

    /** Read-only view of concepts; callers go through {@link #setConcepts}. */
    public List<String> concepts() {
        return concepts == null ? Collections.emptyList()
                                : Collections.unmodifiableList(concepts);
    }

    public void setConcepts(List<String> in) {
        this.concepts = in == null ? new ArrayList<>() : new ArrayList<>(in);
    }
}
