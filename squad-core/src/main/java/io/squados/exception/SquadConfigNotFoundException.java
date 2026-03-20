package io.squados.exception;

public class SquadConfigNotFoundException extends RuntimeException {
    public SquadConfigNotFoundException(String message) {
        super("[SquadOS] Config error: " + message);
    }
}
