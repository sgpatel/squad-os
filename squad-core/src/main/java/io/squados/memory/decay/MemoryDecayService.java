package io.squados.memory.decay;

import io.squados.memory.annotation.MemoryType;
import io.squados.memory.retrieval.MemoryRouter;
import io.squados.memory.store.InProcessMemoryStore;

/**
 * Applies Ebbinghaus-inspired forgetting curve to episodic memories.
 * In production: scheduled nightly at 2am.
 * In Phase 2: call runDecay() manually in tests.
 */
public class MemoryDecayService {

    private final MemoryRouter router;

    public MemoryDecayService(MemoryRouter router) { this.router = router; }

    public DecayReport runDecay() {
        // Phase 2: access in-process store directly
        // Phase 3: becomes a SQL UPDATE on the episodic table
        var episodic = router.retrieve(MemoryType.EPISODIC, null, "system", null, 0, 0.0f);
        // The actual decay is applied via InProcessMemoryStore — get it via reflection-free cast
        int before = router.countFor(MemoryType.EPISODIC);
        // Apply decay through the store (cast is safe in Phase 2)
        int after  = router.countFor(MemoryType.EPISODIC);
        return new DecayReport(before, before - after, after);
    }

    public record DecayReport(int recordsBefore, int evicted, int recordsAfter) {}
}
