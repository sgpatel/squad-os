package io.squados.benchmark;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads benchmark cases from JSON or CSV (classpath or file path).
 *
 * JSON format — array of objects with "input", "expected", optional "tags":
 * <pre>
 * [
 *   {"input": "task text", "expected": "expected output", "tags": ["tag1"]},
 *   ...
 * ]
 * </pre>
 *
 * CSV format — header row: input,expected,tags  (tags pipe-separated):
 * <pre>
 * input,expected,tags
 * "Analyse sentiment of: great!","POSITIVE","pos|product"
 * </pre>
 *
 * Can also be built programmatically via {@link #builder()}.
 */
public class BenchmarkDataset {

    private final List<BenchmarkCase> cases;
    private final String              name;

    private BenchmarkDataset(String name, List<BenchmarkCase> cases) {
        this.name  = name;
        this.cases = Collections.unmodifiableList(cases);
    }

    // ── Loading ───────────────────────────────────────────────────────

    /**
     * Load from classpath or absolute file path.
     * Path starting with "classpath:" strips the prefix and loads from ClassLoader.
     */
    public static BenchmarkDataset load(String path) {
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        InputStream is = openStream(path);
        if (is == null) throw new IllegalArgumentException("Dataset not found: " + path);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String content = readAll(reader).trim();
            List<BenchmarkCase> cases = content.startsWith("[")
                ? parseJson(content) : parseCsv(content);
            return new BenchmarkDataset(name, cases);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load benchmark dataset: " + path, e);
        }
    }

    /** Build a dataset inline (useful for tests). */
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String name = "inline";
        private final List<BenchmarkCase> cases = new ArrayList<>();

        public Builder name(String n)   { this.name = n; return this; }
        public Builder add(String input, String expected) {
            cases.add(BenchmarkCase.of(input, expected)); return this;
        }
        public Builder add(BenchmarkCase c) { cases.add(c); return this; }
        public BenchmarkDataset build()  { return new BenchmarkDataset(name, cases); }
    }

    // ── Accessors ─────────────────────────────────────────────────────

    public List<BenchmarkCase> cases() { return cases; }
    public int                 size()  { return cases.size(); }
    public String              name()  { return name; }

    // ── JSON parser (no external deps) ───────────────────────────────

    public static List<BenchmarkCase> parseJson(String json) {
        List<BenchmarkCase> result = new ArrayList<>();
        // Extract each {...} object from the top-level array
        Pattern obj = Pattern.compile("\\{([^{}]+)\\}");
        Matcher m   = obj.matcher(json);
        while (m.find()) {
            String body   = m.group(1);
            String input  = jsonStr(body, "input");
            String expect = jsonStr(body, "expected");
            List<String> tags = jsonArray(body, "tags");
            if (input != null && expect != null) {
                result.add(BenchmarkCase.of(input, expect, tags));
            }
        }
        return result;
    }

    private static String jsonStr(String obj, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(obj);
        return m.find() ? m.group(1) : null;
    }

    private static List<String> jsonArray(String obj, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher m = p.matcher(obj);
        if (!m.find()) return List.of();
        List<String> items = new ArrayList<>();
        Pattern item = Pattern.compile("\"([^\"]+)\"");
        Matcher mi   = item.matcher(m.group(1));
        while (mi.find()) items.add(mi.group(1));
        return items;
    }

    // ── CSV parser ────────────────────────────────────────────────────

    public static List<BenchmarkCase> parseCsv(String csv) {
        List<BenchmarkCase> result = new ArrayList<>();
        String[] lines = csv.split("\\r?\\n");
        boolean header = true;
        for (String line : lines) {
            if (line.isBlank()) continue;
            if (header) { header = false; continue; } // skip header
            String[] cols = splitCsv(line);
            if (cols.length < 2) continue;
            String input  = unquote(cols[0]);
            String expect = unquote(cols[1]);
            List<String> tags = cols.length > 2
                ? Arrays.asList(unquote(cols[2]).split("\\|")) : List.of();
            result.add(BenchmarkCase.of(input, expect, tags));
        }
        return result;
    }

    private static String[] splitCsv(String line) {
        List<String> parts = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder cur = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') { inQuotes = !inQuotes; }
            else if (c == ',' && !inQuotes) { parts.add(cur.toString()); cur = new StringBuilder(); }
            else cur.append(c);
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }

    private static String unquote(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s;
    }

    private static InputStream openStream(String path) {
        if (path.startsWith("classpath:")) path = path.substring("classpath:".length());
        InputStream is = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream(path);
        if (is != null) return is;
        try { return new FileInputStream(path); } catch (FileNotFoundException e) { return null; }
    }

    private static String readAll(BufferedReader r) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
    }
}
