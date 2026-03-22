package io.squados.tool;

import io.squados.annotation.SquadTool;
import io.squados.annotation.ToolParam;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

/**
 * Discovers all {@literal @}SquadTool methods on an agent instance
 * and builds {@link SquadToolDefinition} descriptors for each.
 *
 * Called once per agent during SquadRunner.boot().
 */
public class SquadToolRegistry {

    private final Map<String, SquadToolDefinition> tools = new LinkedHashMap<>();

    /**
     * Scan an agent instance for @SquadTool methods and register them.
     */
    public void register(Object agentInstance) {
        Class<?> cls = agentInstance.getClass();
        for (Method method : cls.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(SquadTool.class)) continue;
            method.setAccessible(true);
            SquadTool ann  = method.getAnnotation(SquadTool.class);
            String toolName = ann.name().isEmpty() ? method.getName() : ann.name();
            List<SquadToolDefinition.ToolParamDef> params = buildParams(method);
            SquadToolDefinition def = new SquadToolDefinition(
                toolName, ann.description(), method, agentInstance,
                params, ann.maxIterations()
            );
            tools.put(toolName, def);
            System.out.printf("[SquadTool] Registered: %s.%s() as tool \"%s\"%n",
                cls.getSimpleName(), method.getName(), toolName);
        }
    }

    public boolean hasTools()                          { return !tools.isEmpty(); }
    public Map<String, SquadToolDefinition> getAll()   { return Collections.unmodifiableMap(tools); }
    public SquadToolDefinition get(String name)        { return tools.get(name); }

    /**
     * Build the tool definitions prompt appended to the agent system prompt.
     * Tells the LLM about ALL available tools and how to call them.
     */
    public String buildToolsPrompt() {
        if (tools.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== AVAILABLE TOOLS ===\n");
        sb.append("You have access to the following tools. To call a tool, ");
        sb.append("respond with EXACTLY this format on its own line:\n");
        sb.append("TOOL_CALL: <tool_name> {<json_args>}\n\n");
        tools.values().forEach(t -> {
            sb.append(t.toSchemaPrompt()).append("\n\n");
        });
        sb.append("After receiving a TOOL_RESULT, use it to formulate your final response.\n");
        sb.append("=== END TOOLS ===");
        return sb.toString();
    }

    // ── Helpers ──────────────────────────────────────────────────

    private List<SquadToolDefinition.ToolParamDef> buildParams(Method method) {
        List<SquadToolDefinition.ToolParamDef> params = new ArrayList<>();
        Parameter[] parameters = method.getParameters();
        Annotation[][] annotations = method.getParameterAnnotations();
        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            ToolParam ann = null;
            for (Annotation a : annotations[i]) {
                if (a instanceof ToolParam) { ann = (ToolParam) a; break; }
            }
            String desc     = ann != null ? ann.description() : p.getName();
            boolean required = ann == null || ann.required();
            params.add(new SquadToolDefinition.ToolParamDef(
                p.getName(), javaTypeToJson(p.getType()), desc, required
            ));
        }
        return params;
    }

    private String javaTypeToJson(Class<?> type) {
        if (type == String.class)  return "string";
        if (type == int.class || type == Integer.class) return "integer";
        if (type == long.class || type == Long.class) return "integer";
        if (type == double.class || type == Double.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        return "string";
    }
}