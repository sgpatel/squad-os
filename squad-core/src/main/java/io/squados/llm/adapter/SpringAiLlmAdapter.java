package io.squados.llm.adapter;

import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;

/**
 * Production LlmPort implementation backed by Spring AI ChatClient.
 *
 * This is the ONLY class in SquadOS that imports Spring AI classes.
 * Every other class in the framework uses LlmPort only.
 * This is enforced architecturally — SquadOS core has spring-ai-core
 * as an optional dependency; only this adapter activates it.
 *
 * Wiring in your Spring Boot app:
 * <pre>
 * {@literal @}Configuration
 * public class SquadOsConfig {
 *
 *     {@literal @}Bean
 *     public LlmPort llmPort(ChatClient.Builder builder) {
 *         return new SpringAiLlmAdapter(builder);
 *     }
 *
 *     {@literal @}Bean
 *     public SquadContext squadContext(SquadConfig config, LlmPort llmPort) {
 *         SquadContext ctx = new SquadContext(config, llmPort);
 *         ctx.boot();
 *         return ctx;
 *     }
 * }
 * </pre>
 *
 * Required pom.xml dependency:
 * <pre>
 * &lt;dependency&gt;
 *   &lt;groupId&gt;org.springframework.ai&lt;/groupId&gt;
 *   &lt;artifactId&gt;spring-ai-anthropic-spring-boot-starter&lt;/artifactId&gt;
 *   &lt;version&gt;1.0.0&lt;/version&gt;
 * &lt;/dependency&gt;
 * &lt;!-- or openai, ollama, etc. --&gt;
 * </pre>
 *
 * squad.yml llm block maps to Spring AI auto-config:
 *   llm.provider: anthropic  ->  spring.ai.anthropic.chat.enabled=true
 *   llm.model: claude-sonnet-4-6  ->  spring.ai.anthropic.chat.options.model
 */
public class SpringAiLlmAdapter implements LlmPort {

    // Typed as Object to avoid hard compile-time dependency on spring-ai-core
    // when the adapter jar is present but Spring AI is not on the classpath.
    // In practice, if you're using this class Spring AI IS on the classpath.
    private final org.springframework.ai.chat.client.ChatClient chatClient;

    public SpringAiLlmAdapter(
            org.springframework.ai.chat.client.ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * Direct constructor for testing with a pre-built ChatClient.
     */
    public SpringAiLlmAdapter(
            org.springframework.ai.chat.client.ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public LlmResponse chat(String systemPrompt, String userMessage,
                            LlmOptions options) {
        String content = chatClient.prompt()
            .system(systemPrompt)
            .user(userMessage)
            .call()
            .content();

        return new LlmResponse(
            content,
            0, // Spring AI does not expose token counts at this call level
            0, // Wire ChatResponse.getMetadata() in Phase 7 for cost tracking
            options.model() != null ? options.model() : "spring-ai"
        );
    }

    @Override
    public <T> T chatStructured(String systemPrompt, String userMessage,
                                Class<T> responseType, LlmOptions options) {
        return chatClient.prompt()
            .system(systemPrompt)
            .user(userMessage)
            .call()
            .entity(responseType);
    }
}
