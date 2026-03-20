package io.squados.llm.adapter;

import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;

/**
 * Spring AI implementation of LlmPort.
 *
 * This is the ONLY class in SquadOS that imports Spring AI classes.
 * All other agents and framework code use LlmPort only.
 *
 * Wiring (add to SquadContext when spring-ai-core is on the classpath):
 *   LlmPort llm = new SpringAiLlmAdapter(chatClientBuilder);
 *   SquadContext ctx = new SquadContext(config, llm);
 *
 * Dependencies required in pom.xml:
 *   spring-ai-core
 *   spring-ai-anthropic-spring-boot-starter (or openai equivalent)
 */
public class SpringAiLlmAdapter implements LlmPort {

    // Uses Object to avoid compile-time dependency on spring-ai-core
    // when the adapter is not activated. Cast at runtime.
    private final Object chatClientBuilder;
    private Object chatClient;

    public SpringAiLlmAdapter(Object chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
        initClient();
    }

    private void initClient() {
        try {
            // Equivalent to: chatClient = ((ChatClient.Builder) chatClientBuilder).build()
            Class<?> builderClass = chatClientBuilder.getClass();
            java.lang.reflect.Method build = builderClass.getMethod("build");
            this.chatClient = build.invoke(chatClientBuilder);
        } catch (Exception e) {
            throw new RuntimeException(
                "[SquadOS] SpringAiLlmAdapter: failed to build ChatClient. "
                + "Ensure spring-ai-core is on the classpath. Cause: " + e.getMessage(), e);
        }
    }

    @Override
    public LlmResponse chat(String systemPrompt, String userMessage, LlmOptions options) {
        try {
            // chatClient.prompt().system(sys).user(user).call().content()
            Object prompt   = chatClient.getClass().getMethod("prompt").invoke(chatClient);
            Object withSys  = prompt.getClass().getMethod("system", String.class).invoke(prompt, systemPrompt);
            Object withUser = withSys.getClass().getMethod("user", String.class).invoke(withSys, userMessage);
            Object callResult = withUser.getClass().getMethod("call").invoke(withUser);
            String content    = (String) callResult.getClass().getMethod("content").invoke(callResult);
            return new LlmResponse(content);
        } catch (Exception e) {
            throw new RuntimeException("[SquadOS] LLM call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T chatStructured(String systemPrompt, String userMessage,
                                Class<T> responseType, LlmOptions options) {
        try {
            Object prompt   = chatClient.getClass().getMethod("prompt").invoke(chatClient);
            Object withSys  = prompt.getClass().getMethod("system", String.class).invoke(prompt, systemPrompt);
            Object withUser = withSys.getClass().getMethod("user", String.class).invoke(withSys, userMessage);
            Object callResult = withUser.getClass().getMethod("call").invoke(withUser);
            return responseType.cast(
                callResult.getClass().getMethod("entity", Class.class).invoke(callResult, responseType));
        } catch (Exception e) {
            throw new RuntimeException("[SquadOS] Structured LLM call failed: " + e.getMessage(), e);
        }
    }
}
