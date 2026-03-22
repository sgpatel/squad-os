package io.squados.improve;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory FeedbackStore. Resets on restart.
 * Use PgVectorFeedbackStore for persistent semantic search.
 *
 * Similarity metric: keyword overlap (Jaccard-like)
 * Good enough for testing and small datasets.
 */
public class InProcessFeedbackStore implements FeedbackStore {

    private final Map<String, List<FeedbackExample>> store = new ConcurrentHashMap<>();

    @Override
    public void save(FeedbackExample example) {
        store.computeIfAbsent(example.getMethodLabel(),
            k -> new ArrayList<>()).add(example);
        System.out.printf("[Improve] Saved %s example for %s (total: %d)%n",
            example.getLabel(), example.getMethodLabel(),
            store.get(example.getMethodLabel()).size());
    }

    @Override
    public List<FeedbackExample> findSimilarGood(String methodLabel,
                                                  String input, int topK) {
        return findSimilar(methodLabel, input, topK, FeedbackExample.Label.GOOD);
    }

    @Override
    public List<FeedbackExample> findSimilarBad(String methodLabel,
                                                 String input, int topK) {
        return findSimilar(methodLabel, input, topK, FeedbackExample.Label.BAD);
    }

    @Override
    public List<FeedbackExample> findAll(String methodLabel) {
        return Collections.unmodifiableList(
            store.getOrDefault(methodLabel, List.of()));
    }

    @Override
    public void markGood(String exampleId, String note) {
        // In-process store is immutable after save
        // Real store would update the label
        System.out.printf("[Improve] Marked GOOD: %s — %s%n", exampleId, note);
    }

    @Override
    public void markBad(String exampleId, String note) {
        System.out.printf("[Improve] Marked BAD: %s — %s%n", exampleId, note);
    }

    // ── Helpers ──────────────────────────────────────────────────

    private List<FeedbackExample> findSimilar(String methodLabel, String input,
                                               int topK,
                                               FeedbackExample.Label label) {
        List<FeedbackExample> all = store.getOrDefault(methodLabel, List.of());
        Set<String> inputTokens = tokenize(input);

        return all.stream()
            .filter(e -> e.getLabel() == label)
            .sorted(Comparator.comparingDouble(
                e -> -jaccardSimilarity(inputTokens, tokenize(e.getInput())))
            )
            .limit(topK)
            .collect(Collectors.toList());
    }

    /** Jaccard similarity between two token sets. */
    double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /** Tokenize text into lowercase words. */
    Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
            .filter(t -> t.length() > 2)
            .collect(Collectors.toSet());
    }
}