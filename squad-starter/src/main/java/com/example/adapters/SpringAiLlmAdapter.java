package com.example.adapters;

import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Production LlmPort backed by Spring AI ChatClient.
 * Lives in squad-starter — this is the ONLY class that imports Spring AI.
 * squad-core never sees org.springframework.ai.
 */
public class SpringAiLlmAdapter implements LlmPort {

    private final ChatClient chatClient;

    public SpringAiLlmAdapter(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public LlmResponse chat(String systemPrompt, String userMessage,
                            LlmOptions options) {
        String content = chatClient.prompt()
            .system(systemPrompt)
            .user(userMessage)
            .call()
            .content();
        return new LlmResponse(content, 0, 0,
            options.model() != null ? options.model() : "ollama");
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
