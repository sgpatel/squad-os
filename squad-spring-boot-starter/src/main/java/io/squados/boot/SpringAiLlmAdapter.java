package io.squados.boot;

import io.squados.llm.*;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.List;

/**
 * Spring AI adapter for LlmPort.
 *
 * Bridges SquadOS LlmPort → Spring AI ChatClient.
 * Works with any Spring AI-compatible model provider:
 *   Ollama, OpenAI, Anthropic, AWS Bedrock, Azure OpenAI, Google Vertex AI, etc.
 *
 * The provider is determined entirely by which spring-ai-starter-model-* the
 * application adds to its pom — this class never imports a model-specific class.
 *
 * Registered automatically when a ChatClient.Builder bean is present on the
 * classpath (i.e. when the application adds a model starter).
 *
 * Usage in application:
 * <pre>
 * # application.properties
 * squad.llm.provider=openai
 * squad.llm.model=gpt-4o
 * </pre>
 *
 * <pre>
 * &lt;!-- pom.xml — add exactly one model starter --&gt;
 * &lt;dependency&gt;
 *   &lt;groupId&gt;org.springframework.ai&lt;/groupId&gt;
 *   &lt;artifactId&gt;spring-ai-starter-model-openai&lt;/artifactId&gt;
 * &lt;/dependency&gt;
 * </pre>
 */
public class SpringAiLlmAdapter implements LlmPort {

    private final ChatClient chatClient;
    private final String     provider;

    public SpringAiLlmAdapter(ChatClient.Builder builder, String provider) {
        this.chatClient = builder.build();
        this.provider   = provider;
    }

    // ── Single-turn chat ─────────────────────────────────────────────────

    @Override
    public LlmResponse chat(String systemPrompt, String userMessage, LlmOptions opts) {
        var promptSpec = chatClient.prompt()
            .system(systemPrompt)
            .user(userMessage);
        
        // Apply LlmOptions if provided
        if (opts != null) {
            promptSpec = promptSpec.options(
                org.springframework.ai.chat.prompt.ChatOptions.builder()
                    .model(opts.model())
                    .temperature((double) opts.temperature())
                    .maxTokens(opts.maxTokens())
                    .build()
            );
        }
        
        ChatResponse cr = promptSpec.call().chatResponse();
        return toLlmResponse(cr);
    }

    // ── Multi-turn chat with history ─────────────────────────────────────

    @Override
    public LlmResponse chatWithHistory(String systemPrompt, String userMessage,
                                       List<ConversationMessage> history,
                                       LlmOptions opts) {
        if (history == null || history.isEmpty()) {
            return chat(systemPrompt, userMessage, opts);
        }
        // Append conversation history to the system prompt so any provider works
        // without importing provider-specific message types.
        StringBuilder enriched = new StringBuilder(systemPrompt)
            .append("\n\n--- Conversation History ---");
        for (ConversationMessage msg : history) {
            enriched.append("\n").append(msg.role()).append(": ").append(msg.content());
        }
        enriched.append("\n--- End of History ---");
        
        var promptSpec = chatClient.prompt()
            .system(enriched.toString())
            .user(userMessage);
        
        // Apply LlmOptions if provided
        if (opts != null) {
            promptSpec = promptSpec.options(
                org.springframework.ai.chat.prompt.ChatOptions.builder()
                    .model(opts.model())
                    .temperature((double) opts.temperature())
                    .maxTokens(opts.maxTokens())
                    .build()
            );
        }
        
        ChatResponse cr = promptSpec.call().chatResponse();
        return toLlmResponse(cr);
    }

    // ── Streaming chat ────────────────────────────────────────────────────

    @Override
    public void chatStream(String systemPrompt, String userMessage,
                           LlmOptions opts, TokenWriter writer) {
        var promptSpec = chatClient.prompt()
            .system(systemPrompt)
            .user(userMessage);
        
        // Apply LlmOptions if provided
        if (opts != null) {
            promptSpec = promptSpec.options(
                org.springframework.ai.chat.prompt.ChatOptions.builder()
                    .model(opts.model())
                    .temperature((double) opts.temperature())
                    .maxTokens(opts.maxTokens())
                    .build()
            );
        }
        
        // Spring AI streaming via Flux<String> — blockLast() bridges to sync TokenWriter
        promptSpec.stream()
            .content()
            .doOnNext(chunk -> {
                if (chunk != null && !chunk.isEmpty()) {
                    writer.write(StreamToken.of(chunk));
                }
            })
            .doOnComplete(() -> {
                writer.write(StreamToken.last());
                writer.flush();
            })
            .doOnError(e -> {
                writer.write(StreamToken.last(""));
                writer.flush();
            })
            .blockLast();
    }

    // ── Structured output ─────────────────────────────────────────────────

    @Override
    public <T> T chatStructured(String systemPrompt, String userMessage,
                                Class<T> responseType, LlmOptions opts) {
        var promptSpec = chatClient.prompt()
            .system(systemPrompt)
            .user(userMessage);
        
        // Apply LlmOptions if provided
        if (opts != null) {
            promptSpec = promptSpec.options(
                org.springframework.ai.chat.prompt.ChatOptions.builder()
                    .model(opts.model())
                    .temperature((double) opts.temperature())
                    .maxTokens(opts.maxTokens())
                    .build()
            );
        }
        
        return promptSpec.call().entity(responseType);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private LlmResponse toLlmResponse(ChatResponse cr) {
        int prompt = 0, completion = 0;
        try {
            Usage usage = cr.getMetadata().getUsage();
            if (usage != null) {
                prompt     = usage.getPromptTokens()     != null ? usage.getPromptTokens().intValue()     : 0;
                completion = usage.getCompletionTokens() != null ? usage.getCompletionTokens().intValue() : 0;
            }
        } catch (Exception ignored) {}
        return new LlmResponse(
            cr.getResult().getOutput().getText(),
            prompt, completion, provider);
    }
}
