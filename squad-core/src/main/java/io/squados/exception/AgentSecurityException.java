package io.squados.exception;
/** Thrown when @SecureAgent access is denied. */
public class AgentSecurityException extends RuntimeException {
    public AgentSecurityException(String message) { super(message); }
}