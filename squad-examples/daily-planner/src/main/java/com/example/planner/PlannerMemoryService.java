package com.example.planner;

import io.squados.memory.MemoryManager;
import io.squados.memory.annotation.*;
import io.squados.memory.store.MemoryRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages persistent memory for the Daily Planner.
 *
 * Every session:
 *   BEFORE agents run — retrieves past plans similar to today's tasks
 *   AFTER agents run  — stores today's plan for future reference
 *
 * After a week of use, Oracle has seen your patterns:
 *   "User always defers 'learn kubernetes' — it appears in DROP IT every time"
 *   "User consistently prioritises deadline-driven tasks on Monday"
 */
@Component
public class PlannerMemoryService {

    private static final String SQUAD_ID = "daily-planner";
    private static final String AGENT_ID = "planner-memory";

    private final MemoryManager memoryManager;

    @Autowired
    public PlannerMemoryService(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    /**
     * Retrieve past planning sessions similar to today's brain dump.
     * Returns formatted context string ready to prepend to agent prompts.
     *
     * @param brainDump  Today's task list (used as similarity query)
     * @return           Formatted past patterns, or empty string if none yet
     */
    public String getPastPatterns(String brainDump) {
        // Create a synthetic @Memory annotation for retrieval
        Memory readAnn = memoryAnnotation(MemoryType.EPISODIC, MemoryScope.SQUAD, MemoryOp.READ);

        List<MemoryRecord> pastSessions = memoryManager.read(
            readAnn, AGENT_ID, SQUAD_ID, currentSession(), brainDump
        );

        if (pastSessions.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n--- WHAT I KNOW ABOUT YOUR PATTERNS (from past sessions) ---\n");
        for (MemoryRecord r : pastSessions) {
            sb.append("• ").append(r.getContent()).append("\n");
        }
        sb.append("--- END OF PAST PATTERNS ---\n");
        return sb.toString();
    }

    /**
     * Save today's planning session to episodic memory.
     * Called after all three agents have responded.
     *
     * @param brainDump  Original task list
     * @param plan       Planner agent's output
     * @param date       Today's date label
     */
    public void saveSession(String brainDump, String plan, String date) {
        // Save the full session as one memory record
        String content = "Session " + date + ":\n" +
            "Tasks submitted: " + summariseTasks(brainDump) + "\n" +
            "Plan produced: " + truncate(plan, 300);

        Memory writeAnn = memoryAnnotation(MemoryType.EPISODIC, MemoryScope.SQUAD, MemoryOp.WRITE);
        memoryManager.write(writeAnn, AGENT_ID, SQUAD_ID, currentSession(), content);

        // Also save individual "dropped" tasks as LOW importance memories
        // so future sessions know what this user chronically defers
        extractDroppedTasks(plan).forEach(task -> {
            String pattern = "User repeatedly defers: " + task +
                " (dropped on " + date + ")";
            Memory patternAnn = memoryAnnotation(
                MemoryType.EPISODIC, MemoryScope.SQUAD, MemoryOp.WRITE);
            memoryManager.write(patternAnn, AGENT_ID, SQUAD_ID,
                currentSession(), pattern);
        });

        // logged by caller
    }

    public int totalMemories() { return memoryManager.totalMemories(); }

    // ── Helpers ───────────────────────────────────────────────────

    private String currentSession() {
        return "session-" + LocalDate.now().toString();
    }

    private String summariseTasks(String brainDump) {
        String[] lines = brainDump.split("\n");
        if (lines.length <= 3) return brainDump.replace("\n", ", ");
        return lines[0] + ", " + lines[1] + " ... (" + lines.length + " tasks total)";
    }

    private String truncate(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) + "..." : s);
    }

    private List<String> extractDroppedTasks(String plan) {
        // Simple heuristic: lines after "DROP IT" section
        List<String> dropped = new java.util.ArrayList<>();
        if (plan == null) return dropped;
        boolean inDropSection = false;
        for (String line : plan.split("\n")) {
            String trimmed = line.trim().toLowerCase();
            if (trimmed.contains("drop it") || trimmed.contains("drop:")) {
                inDropSection = true; continue;
            }
            if (inDropSection && trimmed.startsWith("do ")) { inDropSection = false; }
            if (inDropSection && (trimmed.startsWith("-") || trimmed.matches("\\d+\\..*"))) {
                dropped.add(line.replaceAll("^[-*\\d.\\s]+", "").trim());
            }
        }
        return dropped;
    }

    private static Memory memoryAnnotation(MemoryType type, MemoryScope scope, MemoryOp op) {
        return new Memory() {
            public Class<Memory> annotationType() { return Memory.class; }
            public MemoryType    type()           { return type; }
            public MemoryScope   scope()          { return scope; }
            public MemoryOp      op()             { return op; }
            public int           topK()           { return 3; }
            public float         minScore()       { return 0.0f; }
            public String[]      tags()           { return new String[]{"daily-plan"}; }
            public Importance    importance()     { return Importance.MEDIUM; }
            public boolean       promote()        { return false; }
        };
    }
}
