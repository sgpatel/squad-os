package com.example.snackthief.adapters;
import io.squados.llm.*;
import org.springframework.ai.chat.client.ChatClient;
public class SpringAiLlmAdapter implements LlmPort {
    private final ChatClient chatClient;
    public SpringAiLlmAdapter(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }
    @Override
    public LlmResponse chat(String sys, String user, LlmOptions opts) {
        return new LlmResponse(
            chatClient.prompt().system(sys).user(user).call().content(),
            0, 0, "ollama");
    }
    @Override
    public <T> T chatStructured(String sys, String user, Class<T> type, LlmOptions opts) {
        return chatClient.prompt().system(sys).user(user).call().entity(type);
    }
}