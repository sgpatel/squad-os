package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Per-agent rate limiting.
 *
 * callsPerMinute — Maximum LLM calls per minute (0 = unlimited)
 * tokensPerHour  — Maximum total tokens per hour (0 = unlimited)
 *
 * Violations throw RateLimitExceededException (not retried).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    int callsPerMinute() default 0;
    int tokensPerHour()  default 0;
}
