package io.sentinel.backend.adapter;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
public class SpringAiLlmAdapter implements LlmPort {
    private final ChatClient chatClient;
    public SpringAiLlmAdapter(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }
    @Override
    public LlmResponse chat(String sys, String user, LlmOptions opts) {
        ChatResponse cr = chatClient.prompt().system(sys).user(user).call().chatResponse();
        int prompt = 0, completion = 0;
        try {
            Usage usage = cr.getMetadata().getUsage();
            if (usage != null) {
                prompt     = usage.getPromptTokens()     != null ? usage.getPromptTokens().intValue()     : 0;
                completion = usage.getCompletionTokens() != null ? usage.getCompletionTokens().intValue() : 0;
            }
        } catch (Exception ignored) {}
        String text = cr.getResult() != null ? cr.getResult().getOutput().getText() : "";
        return new LlmResponse(text, prompt, completion, "ollama");
    }
    @Override
    public <T> T chatStructured(String sys, String user, Class<T> type, LlmOptions opts) {
        return chatClient.prompt().system(sys).user(user).call().entity(type);
    }
}