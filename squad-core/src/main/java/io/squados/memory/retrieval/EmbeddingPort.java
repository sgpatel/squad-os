package io.squados.memory.retrieval;

/**
 * Generates float vector embeddings from text.
 * Same pattern as LlmPort — SquadOS owns the interface, ecosystem implements it.
 * Only EmbeddingAdapters may import LangChain4j or Spring AI embedding classes.
 */
public interface EmbeddingPort {
    float[] embed(String text);
    int     dimensions();
}
