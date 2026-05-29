package io.squados.guardrail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-process audit log for all guardrail violations.
 * Thread-safe; accessible from the dashboard via GuardrailEngine.getAuditLog().
 */
public class GuardrailAuditLog {

    private final List<GuardrailViolation> entries =
        Collections.synchronizedList(new ArrayList<>());

    public void record(GuardrailViolation violation) {
        entries.add(violation);
    }

    public void recordAll(List<GuardrailViolation> violations) {
        entries.addAll(violations);
    }

    public List<GuardrailViolation> getAll() {
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }

    public int size()  { return entries.size(); }
    public void clear(){ entries.clear(); }
}
