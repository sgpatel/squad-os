package com.example.nodeb.adapters;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;
import org.springframework.ai.chat.client.ChatClient;
public class SpringAiLlmAdapter implements LlmPort {
    private final ChatClient chatClient;
    public SpringAiLlmAdapter(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }
    @Override public LlmResponse chat(String s, String u, LlmOptions o) {
        return new LlmResponse(
            chatClient.prompt().system(s).user(u).call().content(),
            0, 0, "ollama");
    }
    @Override public <T> T chatStructured(String s, String u, Class<T> t, LlmOptions o) {
        return chatClient.prompt().system(s).user(u).call().entity(t);
    }
}