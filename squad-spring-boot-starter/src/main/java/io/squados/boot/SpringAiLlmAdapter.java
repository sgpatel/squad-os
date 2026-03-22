package io.squados.boot;

import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Adapts Spring AI ChatClient to SquadOS LlmPort.
 * Auto-configured by SquadAutoConfiguration when Spring AI is on the classpath.
 */
public class SpringAiLlmAdapter implements LlmPort {

    private final ChatClient chatClient;

    public SpringAiLlmAdapter(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public LlmResponse chat(String systemPrompt, String userMessage, LlmOptions opts) {
        String content = chatClient.prompt()
            .system(systemPrompt)
            .user(userMessage)
            .call()
            .content();
        return new LlmResponse(content, 0, 0, "spring-ai");
    }

    @Override
    public <T> T chatStructured(String systemPrompt, String userMessage,
                                 Class<T> responseType, LlmOptions opts) {
        return chatClient.prompt()
            .system(systemPrompt)
            .user(userMessage)
            .call()
            .entity(responseType);
    }
}