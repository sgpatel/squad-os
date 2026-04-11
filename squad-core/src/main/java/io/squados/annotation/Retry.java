package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Retry transient LLM failures with exponential backoff.
 *
 * maxAttempts  — Total attempts including the first (default: 3)
 * backoffMs    — Initial delay between retries in ms (default: 1000)
 * multiplier   — Backoff multiplier (default: 2.0 → 1s, 2s, 4s...)
 * maxBackoffMs — Cap on backoff delay (default: 30000ms)
 *
 * Non-retryable: RateLimitExceededException, GuardrailException, AgentSecurityException
 * Circuit breaker onFailure() is called only after ALL retries are exhausted.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Retry {
    int   maxAttempts()  default 3;
    long  backoffMs()    default 1000L;
    float multiplier()   default 2.0f;
    long  maxBackoffMs() default 30000L;
}
