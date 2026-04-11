package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Golden-set testing annotation with JSON test cases and a pass rate threshold.
 *
 * testCasesPath — Classpath path to a JSON file containing test cases:
 *                 [{"input": "...", "expectedContains": "..."}, ...]
 * passRateMin   — Minimum fraction of test cases that must pass (default: 0.80 = 80%)
 * judgeModel    — LLM model used to evaluate open-ended responses (default: same model)
 *
 * Test case JSON format:
 * <pre>
 *   [
 *     { "input": "What is 2+2?",         "expectedContains": "4" },
 *     { "input": "Capital of France?",   "expectedContains": "Paris" },
 *     { "input": "Summarise this text",  "expectedMinWords": 20 }
 *   ]
 * </pre>
 *
 * Used by EvalRunner in test environments. Not enforced in production.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AgentTest {
    String testCasesPath();
    float  passRateMin()  default 0.80f;
    String judgeModel()   default "";
}
