package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a structured output schema for SquadOS agents.
 *
 * When you call {@code ctx.submit(input, MyPlan.class)}, the agent
 * is prompted to return JSON matching the annotated class structure,
 * which the framework deserialises into a typed Java object.
 *
 * Usage:
 * <pre>
 * {@literal @}SquadPlan
 * public class DayPlan {
 *     private List{@literal <}String{@literal >} doToday;
 *     private List{@literal <}String{@literal >} doLater;
 *     private List{@literal <}String{@literal >} dropIt;
 *     private String verdict;
 *     // getters + setters
 * }
 *
 * // Usage in your app:
 * DayPlan plan = ctx.submit("Plan my day:\n" + tasks, DayPlan.class);
 * System.out.println("Do today: " + plan.getDoToday());
 * </pre>
 *
 * The framework automatically:
 *   1. Builds a JSON schema from the class fields
 *   2. Appends it to the agent's system prompt
 *   3. Instructs the LLM to respond ONLY with valid JSON
 *   4. Deserialises the response into the target class
 *   5. Returns the typed object directly
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface SquadPlan {

    /**
     * Human-readable description of what this plan represents.
     * Included in the LLM prompt so the model understands the context.
     */
    String description() default "";

    /**
     * Whether to validate required fields after deserialisation.
     * Throws {@link io.squados.exception.SquadPlanException} if a
     * {@code @Required} field is null or empty.
     */
    boolean validate() default true;
}
