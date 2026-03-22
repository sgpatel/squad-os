package io.squados.annotation;
/** Access mode for @SecureAgent. */
public enum AccessMode {
    /** Caller must present a valid JWT token with required roles. */
    AUTHENTICATED,
    /** No authentication required. Open access. Audit log still applies. */
    PUBLIC
}