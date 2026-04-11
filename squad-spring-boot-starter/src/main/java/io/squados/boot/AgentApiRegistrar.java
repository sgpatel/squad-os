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
            System.out.printf("[SquadOS] @AgentAPI registered: POST %s/submit  GET %s/info%n",
                path, path);
        }
    }

    private void register(AgentApiController controller) {
        try {
            // Register POST {path}/submit
            Method submit = AgentApiController.class.getMethod(
                "handleSubmit", String.class, String.class);
            RequestMappingInfo submitInfo = RequestMappingInfo
                .paths(controller.getPath() + "/submit")
                .methods(org.springframework.web.bind.annotation.RequestMethod.POST)
                .build();
            handlerMapping.registerMapping(submitInfo, controller, submit);

            // Register GET {path}/info
            Method info = AgentApiController.class.getMethod("handleInfo");
            RequestMappingInfo infoInfo = RequestMappingInfo
                .paths(controller.getPath() + "/info")
                .methods(org.springframework.web.bind.annotation.RequestMethod.GET)
                .build();
            handlerMapping.registerMapping(infoInfo, controller, info);

            // Register GET {path}/health
            Method health = AgentApiController.class.getMethod("handleHealth");
            RequestMappingInfo healthInfo = RequestMappingInfo
                .paths(controller.getPath() + "/health")
                .methods(org.springframework.web.bind.annotation.RequestMethod.GET)
                .build();
            handlerMapping.registerMapping(healthInfo, controller, health);

        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to register @AgentAPI routes: " + e.getMessage(), e);
        }
    }
}
