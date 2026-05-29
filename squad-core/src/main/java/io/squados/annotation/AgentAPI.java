package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Expose this squad as callable HTTP endpoints.
 *
 * path    — Base URL path (e.g. "/api/my-squad")
 * auth    — "none" | "api-key" | "jwt"
 * version — API version string for info endpoint
 *
 * Endpoints registered by AgentApiRegistrar in squad-spring-boot-starter:
 *   POST {path}/submit
 *   POST {path}/submit/{role}
 *   POST {path}/submit/stream
 *   POST {path}/submit/{role}/stream
 *   GET  {path}/info
 *   GET  {path}/health
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AgentAPI {
    String path()    default "/api/squad";
    String auth()    default "none";
    String version() default "1.0";
}
