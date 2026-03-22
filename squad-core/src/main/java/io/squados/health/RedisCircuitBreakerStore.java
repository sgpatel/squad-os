package io.squados.health;

import io.squados.annotation.AgentRole;
import io.squados.memory.store.RedisWorkingStore;

/**
 * Stores circuit breaker state in Redis so all nodes share the same view.
 *
 * When Node A sees Oracle fail 3 times and opens the circuit,
 * Node B immediately sees the circuit as OPEN and won't route to Oracle.
 *
 * Redis key: squad:circuit:{squadId}:{role}
 * Value: CLOSED | OPEN | HALF_OPEN
 * TTL: none (circuit state is permanent until explicitly reset)
 *
 * Usage:
 * <pre>
 * RedisCircuitBreakerStore store = new RedisCircuitBreakerStore(redis, "my-squad");
 * store.setState(AgentRole.STRATEGIST, CircuitState.OPEN);
 * CircuitState state = store.getState(AgentRole.STRATEGIST);
 * </pre>
 */
public class RedisCircuitBreakerStore {

    public enum CircuitState { CLOSED, OPEN, HALF_OPEN }

    private final RedisWorkingStore.RedisCommands redis;
    private final String                          squadId;

    public RedisCircuitBreakerStore(RedisWorkingStore.RedisCommands redis,
                                    String squadId) {
        this.redis   = redis;
        this.squadId = squadId;
    }

    /** Get the current circuit state for a role (default CLOSED if unknown). */
    public CircuitState getState(AgentRole role) {
        String val = redis.hget(key(role), "state");
        if (val == null) return CircuitState.CLOSED;
        try {
            return CircuitState.valueOf(val);
        } catch (IllegalArgumentException e) {
            return CircuitState.CLOSED;
        }
    }

    /** Set the circuit state for a role — visible to all nodes immediately. */
    public void setState(AgentRole role, CircuitState state) {
        redis.hset(key(role), "state", state.name());
        redis.hset(key(role), "updatedAt", String.valueOf(System.currentTimeMillis()));
        redis.hset(key(role), "nodeId", "local");
        System.out.printf("[DistributedCircuit] %s circuit -> %s%n", role, state);
    }

    /** Record a failure for a role. Returns new failure count. */
    public int recordFailure(AgentRole role) {
        String countStr = redis.hget(key(role), "failures");
        int count = countStr == null ? 0 : Integer.parseInt(countStr);
        count++;
        redis.hset(key(role), "failures", String.valueOf(count));
        return count;
    }

    /** Record a success — resets failure count. */
    public void recordSuccess(AgentRole role) {
        redis.hset(key(role), "failures", "0");
        setState(role, CircuitState.CLOSED);
    }

    /** Reset all circuit state for a role. */
    public void reset(AgentRole role) {
        redis.del(key(role));
        System.out.printf("[DistributedCircuit] %s circuit reset%n", role);
    }

    /** Check if a call should be allowed (circuit is CLOSED or HALF_OPEN). */
    public boolean allowCall(AgentRole role) {
        CircuitState state = getState(role);
        return state == CircuitState.CLOSED || state == CircuitState.HALF_OPEN;
    }

    private String key(AgentRole role) {
        return "squad:circuit:" + squadId + ":" + role.name();
    }
}
