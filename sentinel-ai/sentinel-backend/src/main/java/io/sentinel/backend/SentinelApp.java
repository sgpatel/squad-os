package io.sentinel.backend;
import io.sentinel.backend.adapter.SpringAiLlmAdapter;
import io.sentinel.backend.ingestion.TwitterMentionIngestionService;
import io.sentinel.backend.ingestion.FacebookMentionIngestionService;
import io.sentinel.backend.ingestion.InstagramMentionIngestionService;
import io.sentinel.backend.ingestion.LinkedInMentionIngestionService;
import io.sentinel.backend.websocket.MentionWebSocketHandler;
import io.squados.annotation.SquadApplication;
import io.squados.approval.InProcessApprovalStore;
import io.squados.context.SquadContext;
import io.squados.context.SquadRunner;
import io.squados.improve.InProcessFeedbackStore;
import io.squados.llm.LlmPort;
import io.squados.trace.InMemoryTraceExporter;
import io.squados.trace.SquadTracer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@SpringBootApplication(exclude = {
    org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class,
    org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration.class,
    org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration.class,
    org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class,
    org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration.class,
    org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration.class,
    org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration.class,
    org.springframework.ai.model.vertexai.autoconfigure.gemini.VertexAiGeminiChatAutoConfiguration.class
})
@SquadApplication
@EnableScheduling
@EnableWebSocket
public class SentinelApp implements WebSocketConfigurer {

    private final MentionWebSocketHandler wsHandler;
    public SentinelApp(MentionWebSocketHandler wsHandler) { this.wsHandler = wsHandler; }

    public static void main(String[] args) {
        SpringApplication.run(SentinelApp.class, args);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(wsHandler, "/ws/mentions").setAllowedOrigins("*");
    }

    @Bean public LlmPort llmPort(ChatClient.Builder builder) {
        return new SpringAiLlmAdapter(builder);
    }
    @Bean public SquadContext squadContext(LlmPort llmPort) {
        return SquadRunner.run(SentinelApp.class, llmPort);
    }
    @Bean public InMemoryTraceExporter traceExporter() {
        InMemoryTraceExporter exp = new InMemoryTraceExporter();
        SquadTracer.configure(exp); return exp;
    }
    @Bean public InProcessApprovalStore approvalStore() { return new InProcessApprovalStore(); }
    @Bean public InProcessFeedbackStore feedbackStore() { return new InProcessFeedbackStore(); }

    // Start Twitter ingestion on startup if enabled
    @Bean
    public ApplicationRunner twitterStartup(TwitterMentionIngestionService twitter) {
        return args -> twitter.start();
    }

    // Start Facebook ingestion on startup if enabled
    @Bean
    public ApplicationRunner facebookStartup(FacebookMentionIngestionService facebook) {
        return args -> facebook.start();
    }

    // Start Instagram ingestion on startup if enabled
    @Bean
    public ApplicationRunner instagramStartup(InstagramMentionIngestionService instagram) {
        return args -> instagram.start();
    }

    // Start LinkedIn ingestion on startup if enabled
    @Bean
    public ApplicationRunner linkedinStartup(LinkedInMentionIngestionService linkedin) {
        return args -> linkedin.start();
    }
}