package io.sentinel.backend.ingestion;
public interface MentionSource {
    String getName();
    boolean isEnabled();
    void start();
    void stop();
}