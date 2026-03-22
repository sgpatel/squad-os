package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Marks a field in a {@literal @}SquadPlan class as required.
 * If the LLM response is missing this field, a
 * {@link io.squados.exception.SquadPlanException} is thrown.
 *
 * Usage:
 * <pre>
 * {@literal @}SquadPlan(description = "Daily planning output")
 * public class DayPlan {
 *     {@literal @}Required
 *     private List{@literal <}String{@literal >} doToday;
 *
 *     private List{@literal <}String{@literal >} dropIt; // optional
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface Required {
    String message() default "Required field is missing in LLM response";
}
