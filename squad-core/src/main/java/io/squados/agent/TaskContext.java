package io.squados.agent;

import io.squados.memory.store.MemoryRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Carries a task through the SquadOS execution pipeline.
 * Phase 2 addition: memories list, injected by MemoryManager before agent runs.
 */
public class TaskContext {

    private final String taskId;
    private final String taskDescription;
    private final Instant submittedAt;
    private String sessionId;
    private String profile;

    /** Memories retrieved by @Memory — injected before agent method runs */
    private List<MemoryRecord> memories = new ArrayList<>();

    public TaskContext(String taskDescription) {
        this.taskId          = UUID.randomUUID().toString().substring(0, 8);
        this.taskDescription = taskDescription;
        this.submittedAt     = Instant.now();
        this.sessionId       = "session-" + taskId;
        this.profile         = "default";
    }

    public TaskContext(String taskDescription, String sessionId, String profile) {
        this(taskDescription);
        this.sessionId = sessionId;
        this.profile   = profile;
    }

    public String              getTaskId()          { return taskId; }
    public String              getTaskDescription() { return taskDescription; }
    public Instant             getSubmittedAt()     { return submittedAt; }
    public String              getSessionId()       { return sessionId; }
    public String              getProfile()         { return profile; }
    public List<MemoryRecord>  getMemories()        { return Collections.unmodifiableList(memories); }
    public boolean             hasMemories()        { return !memories.isEmpty(); }

    public void setSessionId(String s)                  { this.sessionId = s; }
    public void setProfile(String p)                    { this.profile   = p; }
    public void setMemories(List<MemoryRecord> m)       { this.memories  = new ArrayList<>(m); }
    public void addMemory(MemoryRecord m)                { this.memories.add(m); }

    @Override
    public String toString() {
        return "TaskContext{id='" + taskId
                + "', task='" + taskDescription.substring(0, Math.min(60, taskDescription.length()))
                + (taskDescription.length() > 60 ? "..." : "")
                + "', memories=" + memories.size()
                + ", profile='" + profile + "'}";
    }
}
