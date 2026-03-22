package io.squados.llm.adapter;

import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;

/**
 * Stub — Spring AI adapter moved to consumer modules.
 *
 * squad-core has ZERO runtime dependencies by design.
 * The real SpringAiLlmAdapter lives in squad-starter:
 * {@code com.example.adapters.SpringAiLlmAdapter}
 *
 * To wire a real LLM:
 * <pre>
 * {@literal @}Bean
 * public LlmPort llmPort(ChatClient.Builder builder) {
 *     return new SpringAiLlmAdapter(builder); // in your app module
 * }
 * </pre>
 *
 * @deprecated Use SpringAiLlmAdapter from your application module (squad-starter or squad-examples).
 */
@Deprecated
public class SpringAiLlmAdapter implements LlmPort {

    @Override
    public LlmResponse chat(String systemPrompt, String userMessage, LlmOptions options) {
        throw new UnsupportedOperationException(
            "SpringAiLlmAdapter is a stub in squad-core. " +
            "Use the real adapter from your application module.");
    }

    @Override
    public <T> T chatStructured(String systemPrompt, String userMessage,
                                Class<T> responseType, LlmOptions options) {
        throw new UnsupportedOperationException(
            "SpringAiLlmAdapter is a stub in squad-core.");
    }
}
