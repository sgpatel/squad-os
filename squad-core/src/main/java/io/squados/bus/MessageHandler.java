package io.squados.bus;

/**
 * Functional interface for @OnMessage listener methods.
 * The AgentMessageBus stores one of these per registered listener.
 */
@FunctionalInterface
public interface MessageHandler {
    void handle(AgentMessage message) throws Exception;
}
