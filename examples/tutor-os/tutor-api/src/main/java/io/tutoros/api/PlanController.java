package io.tutoros.api;

import io.squados.context.SquadContext;
import io.squados.agent.AgentResponse;
import io.squados.annotation.AgentRole;
import io.tutoros.agent.CurriculumPlannerAgent;
import io.tutoros.model.*;
import io.tutoros.pipeline.SessionManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Plan Controller — manages study plans for a learner.
 *
 * Study plans are the backbone of the TutorOS learning journey.
 * CurriculumPlannerAgent uses @AutoPlan to iterate until all gaps are
 * covered. The UI Chapter screen reads from these endpoints.
 *
 * Endpoints:
 *   GET  /plan/{learnerId}/{subject}           — get the current study plan
 *   POST /plan/{learnerId}/{subject}/generate  — generate/regenerate a plan
 *   GET  /plan/{learnerId}/{subject}/chapters  — list chapters with status
 *   PUT  /plan/{learnerId}/{subject}/advance   — manually advance to next chapter
 *   GET  /plan/{learnerId}/{subject}/chapter/{n} — get a single chapter detail
 *   POST /plan/{learnerId}/{subject}/refresh   — refresh plan after re-diagnostic
 */
@RestController
@RequestMapping("/plan")
@CrossOrigin(origins = "*")
public class PlanController {

    private final SquadContext           ctx;
    private final CurriculumPlannerAgent planner;
    private final SessionManager         sessionManager;

    /** In-memory plan store — production would use a persistent DB */
    private final Map<String, StudyPlan> planStore = new HashMap<>();

    public PlanController(SquadContext ctx,
                           CurriculumPlannerAgent planner,
                           SessionManager sessionManager) {
        this.ctx            = ctx;
        this.planner        = planner;
        this.sessionManager = sessionManager;
    }

    // ── Get current plan ──────────────────────────────────────────────

    /**
     * GET /plan/{learnerId}/{subject}
     *
     * Returns the active study plan for this learner + subject.
     * 404 if no plan exists yet (trigger POST /generate first).
     */
    @GetMapping("/{learnerId}/{subject}")
    public ResponseEntity<StudyPlan> getPlan(
            @PathVariable String learnerId,
            @PathVariable String subject) {

        StudyPlan plan = planStore.get(planKey(learnerId, subject));
        return plan != null ? ResponseEntity.ok(plan) : ResponseEntity.notFound().build();
    }

    // ── Generate plan ─────────────────────────────────────────────────

    /**
     * POST /plan/{learnerId}/{subject}/generate
     *
     * Triggers CurriculumPlannerAgent (@AutoPlan) to build a new plan.
     * Replaces any existing plan for this learner + subject.
     *
     * Body: GeneratePlanRequest (learner profile + optional override params)
     *
     * The @AutoPlan loop iterates up to 4× until:
     *   - all gap concepts are covered by a chapter
     *   - total hours fit within the learner's weekly session target
     *   - official syllabus requirements are met
     */
    @PostMapping("/{learnerId}/{subject}/generate")
    public ResponseEntity<PlanGenerationResponse> generatePlan(
            @PathVariable String learnerId,
            @PathVariable String subject,
            @RequestBody GeneratePlanRequest request) {

        LearnerProfile profile = request.profile();
        String syllabus = planner.fetchSyllabus(subject, profile.level);

        // Run CurriculumPlannerAgent via SquadContext
        AgentResponse result = ctx.submitTo(AgentRole.STRATEGIST,
            planner.planningPrompt(profile, syllabus)
        );

        StudyPlan plan = result.structuredOutput(StudyPlan.class);
        planStore.put(planKey(learnerId, subject), plan);

        return ResponseEntity.ok(new PlanGenerationResponse(
            plan,
            "Study plan generated: " + countChapters(plan) + " chapters · " +
            plan.estimatedHours + " hours · targeting " + plan.targetGaps
        ));
    }

    // ── Chapter list ──────────────────────────────────────────────────

    /**
     * GET /plan/{learnerId}/{subject}/chapters
     *
     * Returns all chapters with their status (DONE / ACTIVE / LOCKED)
     * and current mastery percentage.
     *
     * The UI Chapter screen renders from this response.
     */
    @GetMapping("/{learnerId}/{subject}/chapters")
    public ResponseEntity<ChapterListResponse> getChapters(
            @PathVariable String learnerId,
            @PathVariable String subject) {

        StudyPlan plan = planStore.get(planKey(learnerId, subject));
        if (plan == null) return ResponseEntity.notFound().build();

        SessionManager.ProgressSnapshot progress =
            sessionManager.getProgress(learnerId, subject);

        List<ChapterItem> chapters = buildChapterItems(plan, progress);
        return ResponseEntity.ok(new ChapterListResponse(
            plan.planId, plan.subject, plan.topic,
            chapters, plan.estimatedHours,
            plan.targetCompletionDate
        ));
    }

    // ── Advance chapter ───────────────────────────────────────────────

    /**
     * PUT /plan/{learnerId}/{subject}/advance
     *
     * Marks the current chapter as DONE and advances to the next.
     * Called automatically when mastery ≥ 85% or manually by the student.
     *
     * Returns the newly active chapter details.
     */
    @PutMapping("/{learnerId}/{subject}/advance")
    public ResponseEntity<ChapterAdvanceResponse> advanceChapter(
            @PathVariable String learnerId,
            @PathVariable String subject) {

        StudyPlan plan = planStore.get(planKey(learnerId, subject));
        if (plan == null) return ResponseEntity.notFound().build();

        String[] chapters = plan.chapters.split("\n");
        int current = plan.currentChapterIndex;

        if (current >= chapters.length - 1) {
            return ResponseEntity.ok(new ChapterAdvanceResponse(
                current, current, chapters[current],
                "🎉 You've completed all chapters! Time for a final review quiz.",
                true
            ));
        }

        plan.currentChapterIndex = current + 1;
        planStore.put(planKey(learnerId, subject), plan);

        return ResponseEntity.ok(new ChapterAdvanceResponse(
            current, current + 1, chapters[current + 1],
            "Great work! Moving to: " + chapters[current + 1],
            false
        ));
    }

    // ── Single chapter detail ─────────────────────────────────────────

    /**
     * GET /plan/{learnerId}/{subject}/chapter/{n}
     *
     * Returns detailed info for chapter N:
     *   - title, concepts covered, estimated duration
     *   - mastery for each concept
     *   - recommended resources
     *   - prerequisite chapter
     */
    @GetMapping("/{learnerId}/{subject}/chapter/{n}")
    public ResponseEntity<ChapterDetailResponse> getChapterDetail(
            @PathVariable String learnerId,
            @PathVariable String subject,
            @PathVariable int n) {

        StudyPlan plan = planStore.get(planKey(learnerId, subject));
        if (plan == null) return ResponseEntity.notFound().build();

        String[] chapters = plan.chapters.split("\n");
        if (n < 0 || n >= chapters.length) return ResponseEntity.notFound().build();

        SessionManager.ProgressSnapshot progress =
            sessionManager.getProgress(learnerId, subject);

        String title    = chapters[n];
        String status   = chapterStatus(n, plan.currentChapterIndex, progress);
        int masteryPct  = n < plan.currentChapterIndex ? 90 :
                          n == plan.currentChapterIndex ? (int)(progress.overallMastery() * 100) : 0;

        return ResponseEntity.ok(new ChapterDetailResponse(
            n, title, status, masteryPct,
            "~" + (plan.estimatedHours * 60 / Math.max(1, chapters.length)) + " min",
            n > 0 ? chapters[n - 1] : "none",
            List.of("Khan Academy: " + extractConcept(title),
                    "Practice questions in the Quiz tab",
                    "Session notes in Progress tab")
        ));
    }

    // ── Refresh plan ──────────────────────────────────────────────────

    /**
     * POST /plan/{learnerId}/{subject}/refresh
     *
     * Regenerates the plan incorporating the latest mastery data.
     * Called after a re-diagnostic (every 5 sessions).
     */
    @PostMapping("/{learnerId}/{subject}/refresh")
    public ResponseEntity<PlanGenerationResponse> refreshPlan(
            @PathVariable String learnerId,
            @PathVariable String subject,
            @RequestBody GeneratePlanRequest request) {
        // Identical to generate — @AutoPlan iteration handles delta computation
        return generatePlan(learnerId, subject, request);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String planKey(String learnerId, String subject) {
        return learnerId + ":" + subject.toLowerCase().replace(" ", "-");
    }

    private int countChapters(StudyPlan plan) {
        return plan.chapters.split("\n").length;
    }

    private List<ChapterItem> buildChapterItems(StudyPlan plan,
                                                 SessionManager.ProgressSnapshot progress) {
        String[] chapters = plan.chapters.split("\n");
        List<ChapterItem> items = new ArrayList<>();
        for (int i = 0; i < chapters.length; i++) {
            String status = chapterStatus(i, plan.currentChapterIndex, progress);
            int mastery   = i < plan.currentChapterIndex ? 90 :
                            i == plan.currentChapterIndex
                                ? (int)(progress.overallMastery() * 100) : 0;
            items.add(new ChapterItem(i, chapters[i], status, mastery));
        }
        return items;
    }

    private String chapterStatus(int idx, int currentIdx,
                                  SessionManager.ProgressSnapshot progress) {
        if (idx < currentIdx) return "DONE";
        if (idx == currentIdx) return "ACTIVE";
        return "LOCKED";
    }

    private String extractConcept(String chapterTitle) {
        int colon = chapterTitle.indexOf(':');
        return colon >= 0 ? chapterTitle.substring(colon + 1).strip() : chapterTitle;
    }

    // ── Request / Response records ────────────────────────────────────

    public record GeneratePlanRequest(LearnerProfile profile) {}

    public record PlanGenerationResponse(StudyPlan plan, String message) {}

    public record ChapterItem(int index, String title, String status, int masteryPct) {}

    public record ChapterListResponse(
        String planId, String subject, String topic,
        List<ChapterItem> chapters, int estimatedHours, String targetDate
    ) {}

    public record ChapterAdvanceResponse(
        int previousIndex, int newIndex, String newChapterTitle,
        String message, boolean planComplete
    ) {}

    public record ChapterDetailResponse(
        int index, String title, String status, int masteryPct,
        String estimatedDuration, String prerequisite,
        List<String> resources
    ) {}
}
