package io.squados.exception;

/**
 * Thrown when a {@literal @}SquadPlan deserialisation fails —
 * either because the LLM returned invalid JSON or a required field
 * is missing.
 */
public class SquadPlanException extends RuntimeException {
    public SquadPlanException(String message) { super(message); }
    public SquadPlanException(String message, Throwable cause) { super(message, cause); }
}
