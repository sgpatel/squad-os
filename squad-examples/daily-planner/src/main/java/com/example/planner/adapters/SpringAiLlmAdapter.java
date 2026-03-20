package com.example.planner.adapters;

import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;
import org.springframework.ai.chat.client.ChatClient;

public class SpringAiLlmAdapter implements LlmPort {
    private final ChatClient chatClient;
    public SpringAiLlmAdapter(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }
    @Override
    public LlmResponse chat(String systemPrompt, String userMessage, LlmOptions options) {
        String content = chatClient.prompt()
            .system(systemPrompt).user(userMessage).call().content();
        return new LlmResponse(content, 0, 0, "ollama");
    }
    @Override
    public <T> T chatStructured(String sys, String user, Class<T> type, LlmOptions opts) {
        return chatClient.prompt().system(sys).user(user).call().entity(type);
    }
}
