package io.squados.exception;
/** Thrown when a human rejects an @AwaitApproval request. */
public class ApprovalRejectedException extends RuntimeException {
    private final String requestId;
    private final String reason;
    public ApprovalRejectedException(String requestId, String reason) {
        super("Approval rejected for request " + requestId + ": " + reason);
        this.requestId = requestId;
        this.reason    = reason;
    }
    public String getRequestId() { return requestId; }
    public String getReason()    { return reason; }
}