package io.squados.exception;

public class AgentConfigException extends RuntimeException {
    public AgentConfigException(String message) {
        super("[SquadOS] Agent config error: " + message);
    }
}
