package com.example.nodeb;

import com.example.nodeb.redis.JedisRedisCommands;
import io.squados.agent.AgentResponse;
import io.squados.agent.TaskContext;
import io.squados.annotation.AgentRole;
import io.squados.annotation.SquadApplication;
import io.squados.bus.AgentMessage;
import io.squados.bus.MessageType;
import io.squados.bus.RedisAgentBus;
import io.squados.config.SquadConfigBridge;
import io.squados.context.AgentWrapper;
import io.squados.context.SquadContext;
import io.squados.context.SquadRegistry;
import io.squados.context.SquadRunner;
import io.squados.llm.LlmPort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Node B — BlitzAgent (DPS) + NurseBotAgent (SUPPORT).
 * Subscribes to DIRECTIVE messages from Oracle on Node A via Redis.
 * Processes with local Ollama, publishes TASK_COMPLETE response back via Redis.
 * Start this BEFORE Node A.
 */
@SpringBootApplication
@SquadApplication
public class NodeBApp {

    @Value("${redis.host:localhost}")
    private String redisHost;

    @Value("${redis.port:6379}")
    private int redisPort;

    public static void main(String[] args) {
        SquadConfigBridge.applyToSystemProperties();
        SpringApplication.run(NodeBApp.class, args);
    }

    @Bean
    public JedisRedisCommands jedisRedisCommands() {
        return new JedisRedisCommands(redisHost, redisPort);
    }

    @Bean
    public RedisAgentBus redisAgentBus(JedisRedisCommands redis) {
        return new RedisAgentBus(redis, "multi-node");
    }

    @Bean
    public SquadRegistry squadRegistry(JedisRedisCommands redis) {
        SquadRegistry reg = new SquadRegistry(redis, "multi-node", "node-b");
        reg.register(AgentRole.DPS, "BlitzAgent");
        reg.register(AgentRole.SUPPORT, "NurseBotAgent");
        reg.startHeartbeat();
        return reg;
    }

    @Bean
    public LlmPort llmPort(ChatClient.Builder builder) {
        return new com.example.nodeb.adapters.SpringAiLlmAdapter(builder);
    }

    @Bean
    public SquadContext squadContext(LlmPort llmPort) {
        return SquadRunner.run(NodeBApp.class, llmPort);
    }

    @Bean
    public ApplicationRunner runner(SquadContext ctx,
                                    RedisAgentBus bus) {
        return args -> {
            io.squados.context.AgentRegistry agentRegistry = ctx.getRegistry();
            printBanner();
            System.out.println("[Node B] Specialists ready. Subscribing to missions...\n");

            // Subscribe to DIRECTIVE messages from Oracle
            bus.subscribe(AgentRole.STRATEGIST, MessageType.DIRECTIVE, msg -> {
                String mission = msg.getPayload(String.class);
                System.out.printf("%n[Node B] Mission received: %s%n", mission);

                // Process with Blitz (DPS)
                AgentWrapper blitz = agentRegistry.getByRole(AgentRole.DPS);
                if (blitz != null) {
                    System.out.println("[BlitzAgent] Processing...");
                    AgentResponse blitzResp = blitz.execute(
                        new TaskContext(mission, "blitz-task", "node-b"));
                    // Publish Blitz response back to Oracle on Node A
                    bus.publish(new AgentMessage(
                        AgentRole.DPS, MessageType.TASK_COMPLETE,
                        blitzResp.content()));
                    System.out.println("[BlitzAgent] Response sent via Redis.");
                }

                // Process with NurseBot (SUPPORT)
                AgentWrapper nurse = agentRegistry.getByRole(AgentRole.SUPPORT);
                if (nurse != null) {
                    System.out.println("[NurseBotAgent] Processing...");
                    AgentResponse nurseResp = nurse.execute(
                        new TaskContext(mission, "nurse-task", "node-b"));
                    // Publish NurseBot response back to Oracle on Node A
                    bus.publish(new AgentMessage(
                        AgentRole.SUPPORT, MessageType.TASK_COMPLETE,
                        nurseResp.content()));
                    System.out.println("[NurseBotAgent] Response sent via Redis.");
                }
            });

            System.out.println("[Node B] Waiting for missions from Oracle (Node A)...");
            System.out.println("[Node B] Press Ctrl+C to stop.\n");

            // Keep running until interrupted
            Thread.currentThread().join();
        };
    }

    private void printBanner() {
        System.out.println("\n=======================================================");
        System.out.println("  SquadOS — Multi-Agent AI Framework for Java");
        System.out.println("  Node B — BlitzAgent + NurseBotAgent");
        System.out.println("  Subscribed to Redis Pub/Sub");
        System.out.println("=======================================================");
    }
}