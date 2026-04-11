package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Inject a SquadClient proxy for a remote @AgentAPI squad.
 *
 * url     — Base URL of the remote squad (e.g. "http://analyst-squad:8080/api/analyst")
 * auth    — "none" | "api-key" | "jwt"
 * apiKey  — API key (used when auth="api-key"); may be empty to use SQUAD_API_KEY env var
 * timeoutMs — Request timeout in milliseconds
 *
 * Usage:
 * <pre>
 *   @RemoteSquad(url = "http://analyst-squad:8080/api/analyst", auth = "api-key")
 *   private SquadClient analystSquad;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RemoteSquad {
    String url()       default "";
    String auth()      default "none";
    String apiKey()    default "";
    int    timeoutMs() default 30000;
}
