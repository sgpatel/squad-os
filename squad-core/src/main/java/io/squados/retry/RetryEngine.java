package io.squados.retry;

import io.squados.annotation.Retry;
import io.squados.exception.*;

import java.util.concurrent.Callable;

/**
 * Executes a callable with exponential-backoff retry per @Retry annotation.
 *
 * Non-retryable exceptions (thrown immediately, no retry):
 *   RateLimitExceededException, GuardrailException, AgentSecurityException
 *
 * Circuit breaker onFailure() is called only after ALL retries are exhausted.
 *
 * Usage:
 * <pre>
 *   LlmResponse result = RetryEngine.execute(retryAnnotation, agentName,
 *       () -> llm.chat(system, user, options));
 * </pre>
 */
public class RetryEngine {

    /**
     * Execute a callable with retry per @Retry settings.
     *
     * @param retry      The @Retry annotation (null = no retry, just execute once)
     * @param agentName  Name for error messages
     * @param action     The operation to retry
     * @throws RetryExhaustedException if all attempts fail
     */
    public static <T> T execute(Retry retry, String agentName, Callable<T> action) {
        if (retry == null) {
            return executeOnce(action, agentName);
        }

        int   maxAttempts  = retry.maxAttempts();
        long  backoffMs    = retry.backoffMs();
        float multiplier   = retry.multiplier();
        long  maxBackoffMs = retry.maxBackoffMs();

        long  start        = System.currentTimeMillis();
        Throwable lastCause = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.call();
            } catch (RateLimitExceededException | GuardrailException | AgentSecurityException e) {
                // Non-retryable — propagate immediately
                throw (RuntimeException) e;
            } catch (Exception e) {
                lastCause = e;
                if (attempt < maxAttempts) {
                    long delay = Math.min((long)(backoffMs * Math.pow(multiplier, attempt - 1)), maxBackoffMs);
                    try { Thread.sleep(delay); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        throw new RetryExhaustedException(agentName, maxAttempts, elapsed,
            lastCause != null ? lastCause : new RuntimeException("Unknown error"));
    }

    private static <T> T executeOnce(Callable<T> action, String agentName) {
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Agent '" + agentName + "' failed: " + e.getMessage(), e);
        }
    }
}
