package io.squados.remote;

import io.squados.annotation.RemoteSquad;

import java.lang.reflect.Field;

/**
 * Injects SquadClient proxy instances into fields annotated with @RemoteSquad.
 *
 * In standalone (non-Spring) mode, call inject(Object) manually.
 * In Spring Boot mode, RemoteSquadInjector (BeanPostProcessor) handles this automatically.
 */
public class RemoteSquadInvoker {

    /**
     * Inject SquadClient into all @RemoteSquad fields of the target object.
     */
    public static void inject(Object target) {
        Class<?> cls = target.getClass();
        for (Field field : cls.getDeclaredFields()) {
            RemoteSquad ann = field.getAnnotation(RemoteSquad.class);
            if (ann == null) continue;
            if (!field.getType().isAssignableFrom(SquadClient.class)) {
                throw new IllegalArgumentException(
                    "@RemoteSquad field '" + field.getName() + "' in "
                    + cls.getSimpleName() + " must be of type SquadClient");
            }
            AgentAuthProvider auth = resolveAuth(ann);
            SquadClient client = new SquadClient(ann.url(), auth, ann.timeoutMs());
            try {
                field.setAccessible(true);
                field.set(target, client);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to inject @RemoteSquad into "
                    + field.getName(), e);
            }
        }
    }

    private static AgentAuthProvider resolveAuth(RemoteSquad ann) {
        return switch (ann.auth()) {
            case "api-key" -> {
                String key = ann.apiKey().isBlank()
                    ? System.getenv().getOrDefault("SQUAD_API_KEY", "")
                    : ann.apiKey();
                yield new ApiKeyAuth(key);
            }
            case "jwt" -> {
                String token = System.getenv().getOrDefault("SQUAD_JWT_TOKEN", "");
                yield new JwtAuth(token);
            }
            default -> new NoAuth();
        };
    }
}
