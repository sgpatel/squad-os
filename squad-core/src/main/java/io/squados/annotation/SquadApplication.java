package io.squados.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the main class of a SquadOS application.
 *
 * Usage:
 * <pre>
 * {@literal @}SquadApplication
 * public class Main {
 *     public static void main(String[] args) {
 *         SquadContext ctx = SquadApplication.run(Main.class, args);
 *         AgentResponse r  = ctx.submit("My task here");
 *         System.out.println(r.content());
 *     }
 * }
 * </pre>
 *
 * Triggers: classpath scan for {@literal @}Agent classes,
 * squad.yml loading, SquadContext boot sequence.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SquadApplication {

    /**
     * Base packages to scan for @Agent classes.
     * Defaults to the package of the annotated class.
     */
    String[] scanPackages() default {};
}
