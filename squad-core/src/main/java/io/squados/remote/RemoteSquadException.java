package io.squados.remote;

/**
 * Thrown when a remote squad call via @RemoteSquad fails.
 */
public class RemoteSquadException extends RuntimeException {

    private final String remoteUrl;
    private final int    statusCode; // HTTP status, or -1 for non-HTTP errors

    public RemoteSquadException(String remoteUrl, String message) {
        super(message);
        this.remoteUrl  = remoteUrl;
        this.statusCode = -1;
    }

    public RemoteSquadException(String remoteUrl, int statusCode, String message) {
        super("[HTTP " + statusCode + "] " + message + " @ " + remoteUrl);
        this.remoteUrl  = remoteUrl;
        this.statusCode = statusCode;
    }

    public RemoteSquadException(String remoteUrl, String message, Throwable cause) {
        super(message, cause);
        this.remoteUrl  = remoteUrl;
        this.statusCode = -1;
    }

    public String getRemoteUrl()  { return remoteUrl; }
    public int    getStatusCode() { return statusCode; }
}
