package com.example.nodea;

import com.example.nodea.redis.JedisRedisCommands;
import io.squados.annotation.AgentRole;
import io.squados.annotation.SquadApplication;
import io.squados.bus.AgentMessage;
import io.squados.bus.MessageType;
import io.squados.bus.RedisAgentBus;
import io.squados.config.SquadConfigBridge;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
@SquadApplication
public class NodeAApp {

    @Value("${redis.host:localhost}")
    private String redisHost;
    @Value("${redis.port:6379}")
    private int redisPort;

    public static void main(String[] args) {
        SquadConfigBridge.applyToSystemProperties();
        SpringApplication.run(NodeAApp.class, args);
    }

    @Bean public JedisRedisCommands jedisRedisCommands() {
        return new JedisRedisCommands(redisHost, redisPort);
    }

    @Bean public RedisAgentBus redisAgentBus(JedisRedisCommands redis) {
        return new RedisAgentBus(redis, "multi-node");
    }

    @Bean public SquadRegistry squadRegistry(JedisRedisCommands redis) {
        SquadRegistry reg = new SquadRegistry(redis, "multi-node", "node-a");
        reg.register(AgentRole.STRATEGIST, "Oracle");
        reg.startHeartbeat();
        return reg;
    }

    @Bean public LlmPort llmPort(ChatClient.Builder builder) {
        return new com.example.nodea.adapters.SpringAiLlmAdapter(builder);
    }

    @Bean public SquadContext squadContext(LlmPort llmPort) {
        return SquadRunner.run(NodeAApp.class, llmPort);
    }

    @Bean
    public ApplicationRunner runner(SquadContext ctx,
                                    RedisAgentBus bus,
                                    SquadRegistry registry) {
        return args -> {
            printBanner();
            System.out.println("[Oracle] Waiting for specialists on Node B...");
            for (int i = 0; i < 12; i++) {
                long nodeBCount = registry.findAll().stream()
                    .filter(r -> r.nodeId().startsWith("node-b")).count();
                if (nodeBCount >= 2) break;
                System.out.printf("[Oracle] Found %d specialist(s) — waiting...%n", nodeBCount);
                Thread.sleep(2500);
            }
            registry.findAll().forEach(r ->
                System.out.printf("  ✓ %-20s [%s] on %s%n", r.name(), r.role(), r.nodeId()));

            // Mission counter for unique reply channels
            AtomicInteger missionId = new AtomicInteger(0);

            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("\nEnter mission (or quit): ");
                String mission = scanner.nextLine().trim();
                if (mission.equalsIgnoreCase("quit")) break;
                if (mission.isEmpty()) continue;

                // Fresh latch and response map for each mission
                int mid = missionId.incrementAndGet();
                Map<String, String> responses = new ConcurrentHashMap<>();
                CountDownLatch latch = new CountDownLatch(2);

                // Subscribe BEFORE publishing so we never miss a response
                // Use unique handler keys per mission to avoid stale handlers
                io.squados.bus.MessageHandler blitzHandler = msg -> {
                    if (responses.putIfAbsent("Blitz", msg.getPayload(String.class)) == null) {
                        latch.countDown();
                    }
                };
                io.squados.bus.MessageHandler nurseHandler = msg -> {
                    if (responses.putIfAbsent("NurseBot", msg.getPayload(String.class)) == null) {
                        latch.countDown();
                    }
                };

                bus.subscribe(AgentRole.DPS,     MessageType.TASK_COMPLETE, blitzHandler);
                bus.subscribe(AgentRole.SUPPORT,  MessageType.TASK_COMPLETE, nurseHandler);

                System.out.println("[Oracle] Broadcasting mission via Redis...\n");
                bus.publish(new AgentMessage(AgentRole.STRATEGIST, MessageType.DIRECTIVE, mission));

                // Wait for both responses (60s timeout)
                boolean received = latch.await(60, TimeUnit.SECONDS);
                bus.unsubscribe(AgentRole.DPS,    MessageType.TASK_COMPLETE);
                bus.unsubscribe(AgentRole.SUPPORT, MessageType.TASK_COMPLETE);

                if (!received) {
                    System.out.println("[Oracle] Timeout — only received " +
                        responses.size() + "/2 responses.");
                    responses.forEach((a, r) -> System.out.println("[" + a + "]: " + r));
                } else {
                    System.out.println("\n" + "=".repeat(52));
                    System.out.println("  MISSION BRIEF — assembled from squad");
                    System.out.println("=".repeat(52));
                    responses.forEach((agent, resp) ->
                        System.out.println("\n[" + agent + "]:\n" + resp));
                    System.out.println("\n" + "=".repeat(52));
                }
            }
            System.out.println("[Oracle] Signing off.");
        };
    }

    private void printBanner() {
        System.out.println("\n=======================================================");
        System.out.println("  SquadOS — Multi-Agent AI Framework for Java");
        System.out.println("  Node A — Oracle (STRATEGIST) — Redis Pub/Sub");
        System.out.println("=======================================================");
    }
}