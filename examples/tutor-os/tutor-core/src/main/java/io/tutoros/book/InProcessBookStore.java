package io.tutoros.book;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default {@link BookRepository} — a {@code ConcurrentHashMap} keyed
 * by {@link Book#id}. Loses everything on JVM restart; production
 * deployments swap in {@link JdbcBookStore} via the
 * {@code tutor.book.store=jdbc} property.
 *
 * <p>Sort behaviour mirrors what learners expect: most-recently-uploaded
 * book first, so the library page surfaces the latest study material
 * without forcing the learner to scroll.
 */
public class InProcessBookStore implements BookRepository {

    private final Map<String, Book> rows = new ConcurrentHashMap<>();

    @Override
    public void save(Book book) {
        if (book == null || book.id == null || book.id.isBlank()) {
            throw new IllegalArgumentException("Book.id is required");
        }
        rows.put(book.id, book);
    }

    @Override
    public Optional<Book> findById(String bookId) {
        if (bookId == null) return Optional.empty();
        return Optional.ofNullable(rows.get(bookId));
    }

    @Override
    public List<Book> listForLearner(String learnerId) {
        if (learnerId == null) return Collections.emptyList();
        return rows.values().stream()
            .filter(b -> learnerId.equals(b.learnerId))
            // Newest first — uploadedAt is set by the extraction service.
            .sorted(Comparator.comparing(
                (Book b) -> b.uploadedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());
    }

    @Override
    public List<Book> listForSubject(String learnerId, String subject) {
        if (learnerId == null || subject == null) return Collections.emptyList();
        String key = subject.trim().toLowerCase();
        return listForLearner(learnerId).stream()
            .filter(b -> key.equals(b.subject))
            .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String bookId) {
        if (bookId != null) rows.remove(bookId);
    }

    @Override
    public int countForLearner(String learnerId) {
        if (learnerId == null) return 0;
        int n = 0;
        for (Book b : rows.values()) if (learnerId.equals(b.learnerId)) n++;
        return n;
    }
}
