package io.squados.boot;

import io.squados.annotation.RemoteSquad;
import io.squados.remote.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;

/**
 * Spring BeanPostProcessor that injects {@link SquadClient} proxies
 * into fields annotated with {@link RemoteSquad}.
 *
 * Automatically registered as a bean by {@link SquadAutoConfiguration}
 * when squad-spring-boot-starter is on the classpath.
 *
 * Usage in agent class:
 * <pre>
 *   {@literal @}Agent(role = AgentRole.STRATEGIST)
 *   public class OrchestratorAgent {
 *
 *       {@literal @}RemoteSquad(url = "http://analyst-squad:8080/api/analyst", auth = "api-key")
 *       private SquadClient analystSquad;
 *   }
 * </pre>
 */
public class RemoteSquadInjector implements BeanPostProcessor {

    private final String globalApiKey;

    public RemoteSquadInjector(String globalApiKey) {
        this.globalApiKey = globalApiKey;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName)
            throws BeansException {
        Class<?> cls = bean.getClass();
        for (Field field : cls.getDeclaredFields()) {
            RemoteSquad ann = field.getAnnotation(RemoteSquad.class);
            if (ann == null) continue;
            if (!field.getType().isAssignableFrom(SquadClient.class)) continue;

            AgentAuthProvider auth = resolveAuth(ann);
            SquadClient client = new SquadClient(ann.url(), auth, ann.timeoutMs());
            try {
                field.setAccessible(true);
                field.set(bean, client);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(
                    "Failed to inject @RemoteSquad into " + cls.getSimpleName()
                    + "." + field.getName(), e);
            }
        }
        return bean;
    }

    private AgentAuthProvider resolveAuth(RemoteSquad ann) {
        return switch (ann.auth()) {
            case "api-key" -> {
                String key = !ann.apiKey().isBlank() ? ann.apiKey()
                    : !globalApiKey.isBlank() ? globalApiKey
                    : System.getenv().getOrDefault("SQUAD_API_KEY", "");
                yield new ApiKeyAuth(key);
            }
            case "jwt" -> new JwtAuth(
                System.getenv().getOrDefault("SQUAD_JWT_TOKEN", ""));
            default -> new NoAuth();
        };
    }
}
