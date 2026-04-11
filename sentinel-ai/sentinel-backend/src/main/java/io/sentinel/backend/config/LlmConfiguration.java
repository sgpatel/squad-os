package io.sentinel.backend.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Optional;

@Configuration
public class LlmConfiguration {

    @Value("${squad.llm.provider:ollama}")
    private String provider;

    @Primary
    @Bean
    public ChatModel chatModel(
            OllamaChatModel ollamaChatModel,
            Optional<OpenAiChatModel> openAiChatModel,
            Optional<AnthropicChatModel> anthropicChatModel,
            Optional<VertexAiGeminiChatModel> vertexAiGeminiChatModel) {

        ChatModel selectedModel = switch (provider.toLowerCase()) {
            case "openai" -> openAiChatModel.orElse(null);
            case "anthropic", "claude" -> anthropicChatModel.orElse(null);
            case "vertexai", "gemini" -> vertexAiGeminiChatModel.orElse(null);
            default -> ollamaChatModel;
        };

        return selectedModel != null ? selectedModel : ollamaChatModel;
    }
}
