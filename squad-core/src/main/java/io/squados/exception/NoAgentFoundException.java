package io.squados.exception;

public class NoAgentFoundException extends RuntimeException {
    public NoAgentFoundException(String scannedPackages) {
        super("[SquadOS] No @Agent classes found. "
            + "Scanned: " + scannedPackages + ". "
            + "Ensure your agent class is annotated with @Agent(role=...) "
            + "and is within the scanned package.");
    }
}
