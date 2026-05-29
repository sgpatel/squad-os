package io.squados.boot;

import io.squados.llm.LlmPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Separate configuration for LlmPort to avoid early instantiation issues.
 *
 * When SquadAutoConfiguration contains a non-static @Bean BeanPostProcessor
 * (squadRemoteSquadInjector), Spring instantiates it early — before
 * ChatClient.Builder is registered. This causes @ConditionalOnBean to fail.
 *
 * Solution: isolate LlmPort creation in its own @AutoConfiguration that runs
 * in the normal phase, after all Spring AI beans are registered.
 */
@AutoConfiguration
@EnableConfigurationProperties(SquadProperties.class)
public class LlmPortConfig {

    private final SquadProperties props;

    public LlmPortConfig(SquadProperties props) {
        this.props = props;
    }

    @Bean
    @ConditionalOnMissingBean(LlmPort.class)
    @ConditionalOnClass(name = "org.springframework.ai.chat.client.ChatClient")
    public LlmPort squadLlmPort(
            org.springframework.ai.chat.client.ChatClient.Builder builder) {
        String provider = props.getLlm().getProvider();
        String model    = props.getLlm().getModel();
        
        System.out.printf("[SquadOS] LlmPort: Spring AI %s / %s (options applied per-agent)%n", 
            provider, model);
        
        return new SpringAiLlmAdapter(builder, provider);
    }
}
