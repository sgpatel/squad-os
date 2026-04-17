package io.tutoros.api;

import io.squados.annotation.SquadApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TutorOS Spring Boot entry point.
 *
 * @SquadApplication triggers SquadOS auto-configuration:
 *   - All agents registered in AgentRegistry at boot
 *   - @AgentMemory store initialised (InProcessMemoryStore by default)
 *   - @DurableAgent checkpoint store initialised
 *   - GuardrailEngine wired with configured filters
 *   - DebateEngine wired with SocraticTutor + DirectTutor participants
 *   - OtelSpanExporter connected to OTLP endpoint (if squad.otel.enabled=true)
 *   - McpServer started on squad.mcp.server.port (if squad.mcp.server.enabled=true)
 */
@SpringBootApplication(scanBasePackages = "io.tutoros")
@SquadApplication
public class TutorOsApplication {
    public static void main(String[] args) {
        SpringApplication.run(TutorOsApplication.class, args);
    }
}
