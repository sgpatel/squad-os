package io.squados.boot;

import io.squados.annotation.AgentAPI;
import io.squados.context.AgentWrapper;
import io.squados.context.SquadContext;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * Scans registered agents for @AgentAPI annotation and registers
 * dynamic Spring MVC routes via RequestMappingHandlerMapping.
 *
 * Called by SquadAutoConfiguration after SquadContext is booted.
 *
 * Routes registered per @AgentAPI agent:
 *   POST {path}/submit
 *   POST {path}/submit/{role}
 *   POST {path}/submit/stream
 *   POST {path}/submit/{role}/stream
 *   GET  {path}/info
 *   GET  {path}/health
 */
public class AgentApiRegistrar {

    private final SquadContext                  context;
    private final RequestMappingHandlerMapping  handlerMapping;
    private final String                        globalApiKey;

    public AgentApiRegistrar(SquadContext context,
                              RequestMappingHandlerMapping handlerMapping,
                              String globalApiKey) {
        this.context       = context;
        this.handlerMapping = handlerMapping;
        this.globalApiKey  = globalApiKey;
    }

    /**
     * Scan all registered agents for @AgentAPI and register HTTP routes.
     */
    public void registerAll() {
        for (AgentWrapper wrapper : context.getRegistry().all()) {
            AgentAPI ann = wrapper.getAgentClass().getAnnotation(AgentAPI.class);
            if (ann == null) continue;

            String path   = ann.path();
            String apiKey = !ann.auth().equals("none") ? globalApiKey : "";

            AgentApiController controller = new AgentApiController(context, path, apiKey);
            register(controller);
            System.out.printf(
                "[SquadOS] @AgentAPI registered: %s  [submit, submit/{role}, submit/stream, submit/{role}/stream, info, health]%n",
                path);
        }
    }

    private void register(AgentApiController controller) {
        try {
            String base = controller.getPath();
            var POST = org.springframework.web.bind.annotation.RequestMethod.POST;
            var GET  = org.springframework.web.bind.annotation.RequestMethod.GET;

            // POST {path}/submit
            handlerMapping.registerMapping(
                RequestMappingInfo.paths(base + "/submit").methods(POST).build(),
                controller,
                AgentApiController.class.getMethod("handleSubmit", String.class, String.class));

            // POST {path}/submit/{role}
            handlerMapping.registerMapping(
                RequestMappingInfo.paths(base + "/submit/{role}").methods(POST).build(),
                controller,
                AgentApiController.class.getMethod("handleSubmitToRole",
                    String.class, String.class, String.class));

            // POST {path}/submit/stream
            handlerMapping.registerMapping(
                RequestMappingInfo.paths(base + "/submit/stream").methods(POST).build(),
                controller,
                AgentApiController.class.getMethod("handleStream", String.class, String.class));

            // POST {path}/submit/{role}/stream
            handlerMapping.registerMapping(
                RequestMappingInfo.paths(base + "/submit/{role}/stream").methods(POST).build(),
                controller,
                AgentApiController.class.getMethod("handleStreamToRole",
                    String.class, String.class, String.class));

            // GET {path}/info
            handlerMapping.registerMapping(
                RequestMappingInfo.paths(base + "/info").methods(GET).build(),
                controller,
                AgentApiController.class.getMethod("handleInfo"));

            // GET {path}/health
            handlerMapping.registerMapping(
                RequestMappingInfo.paths(base + "/health").methods(GET).build(),
                controller,
                AgentApiController.class.getMethod("handleHealth"));

        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to register @AgentAPI routes: " + e.getMessage(), e);
        }
    }
}
