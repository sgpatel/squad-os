package io.squados.structured;

import io.squados.annotation.OutputField;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Generates a JSON schema prompt from a plain Java class.
 *
 * Reflects over public (and package) fields, using {@link OutputField}
 * annotations for descriptions and examples where present.
 *
 * Output example for a class with fields {sentiment:String, confidence:double}:
 * <pre>
 * Respond ONLY with valid JSON matching this schema:
 * {
 *   "sentiment":  "<string> overall sentiment label — e.g. POSITIVE",
 *   "confidence": "<number> confidence score 0.0–1.0 — e.g. 0.92"
 * }
 * Do not include any prose, markdown, or explanation outside the JSON object.
 * </pre>
 */
public class JsonSchemaGenerator {

    private JsonSchemaGenerator() {}

    /**
     * Build a prompt block that instructs the LLM to produce JSON
     * conforming to {@code schemaClass}.
     */
    public static String buildSchemaPrompt(Class<?> schemaClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== STRUCTURED OUTPUT REQUIRED ===\n");
        sb.append("Respond ONLY with a valid JSON object matching this schema:\n");
        sb.append(buildJsonTemplate(schemaClass));
        sb.append("\n\nRules:\n");
        sb.append("- Output ONLY the JSON object. No markdown fences, no prose.\n");
        sb.append("- All required fields must be present.\n");
        sb.append("- Use null for optional fields you cannot determine.\n");
        sb.append("=== END SCHEMA ===\n");
        return sb.toString();
    }

    /**
     * Build an annotated JSON template string showing field names, types,
     * descriptions, and examples.
     */
    public static String buildJsonTemplate(Class<?> schemaClass) {
        List<Field> fields = collectFields(schemaClass);
        if (fields.isEmpty()) return "{}";

        StringBuilder sb = new StringBuilder("{\n");
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            OutputField ann = f.getAnnotation(OutputField.class);
            String typeHint = jsonTypeName(f.getType());
            String comment  = buildFieldComment(f.getName(), typeHint, ann);
            sb.append("  \"").append(f.getName()).append("\": ").append(comment);
            if (i < fields.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Return the list of field names declared (directly) on schemaClass.
     * Used by the parser to validate extracted JSON contains required fields.
     */
    public static List<String> requiredFields(Class<?> schemaClass) {
        List<String> result = new ArrayList<>();
        for (Field f : collectFields(schemaClass)) {
            OutputField ann = f.getAnnotation(OutputField.class);
            if (ann == null || ann.required()) {
                result.add(f.getName());
            }
        }
        return result;
    }

    // ── Internals ─────────────────────────────────────────────────────

    static List<Field> collectFields(Class<?> cls) {
        List<Field> result = new ArrayList<>();
        for (Field f : cls.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.isSynthetic()) continue;
            result.add(f);
        }
        return result;
    }

    private static String buildFieldComment(String name, String type, OutputField ann) {
        StringBuilder sb = new StringBuilder("\"<").append(type).append(">");
        if (ann != null && !ann.description().isEmpty()) {
            sb.append(" ").append(ann.description());
        }
        if (ann != null && !ann.example().isEmpty()) {
            sb.append(" — e.g. ").append(ann.example());
        }
        if (ann != null && !ann.required()) {
            sb.append(" (optional)");
        }
        sb.append("\"");
        return sb.toString();
    }

    private static String jsonTypeName(Class<?> type) {
        if (type == String.class)                                 return "string";
        if (type == int.class || type == Integer.class)           return "integer";
        if (type == long.class || type == Long.class)             return "integer";
        if (type == double.class || type == Double.class)         return "number";
        if (type == float.class || type == Float.class)           return "number";
        if (type == boolean.class || type == Boolean.class)       return "boolean";
        if (List.class.isAssignableFrom(type))                    return "array";
        if (Map.class.isAssignableFrom(type))                     return "object";
        return "string";
    }
}
