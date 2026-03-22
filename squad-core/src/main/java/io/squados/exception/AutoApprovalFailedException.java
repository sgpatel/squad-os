package io.squados.exception;
/** Thrown when @AutoApproval condition fails and no @AwaitApproval fallback exists. */
public class AutoApprovalFailedException extends RuntimeException {
    public AutoApprovalFailedException(String condition, String rejectReason) {
        super("AutoApproval condition failed [" + condition + "]: " + rejectReason);
    }
}