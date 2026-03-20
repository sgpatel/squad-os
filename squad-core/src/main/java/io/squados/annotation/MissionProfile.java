package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Activates a squad with a named mission profile.
 * Equivalent to Spring's @Profile — different agent personalities
 * and routing rules activate based on the profile value.
 *
 * Supported built-in profiles: gaming, work, creative, default
 * Custom profiles: any string value, matched against squad.yml profile field.
 *
 * Example:
 * <pre>
 * {@literal @}Agent(role = AgentRole.DPS, name = "Blitz")
 * {@literal @}MissionProfile("gaming")
 * public class BlitzAgent { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MissionProfile {
    String value();
}
