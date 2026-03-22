package io.squados.agent;

import io.squados.annotation.Required;
import io.squados.annotation.SquadPlan;
import io.squados.exception.SquadPlanException;

import java.lang.reflect.*;
import java.util.*;

/**
 * Deserialises a JSON string from LLM response into a typed @SquadPlan object.
 * No Jackson/Gson — pure reflection + minimal JSON parsing.
 * Supported types: String, int, long, boolean, List<String>
 */
public class SquadPlanDeserialiser {

    private SquadPlanDeserialiser() {}

    public static <T> T deserialise(String json, Class<T> targetClass) {
        if (!targetClass.isAnnotationPresent(SquadPlan.class)) {
            throw new SquadPlanException(
                targetClass.getSimpleName() + " must be annotated with @SquadPlan");
        }
        String cleaned = stripCodeFences(json.trim());
        try {
            T instance = targetClass.getDeclaredConstructor().newInstance();
            Map<String, String> rawFields = parseJsonFields(cleaned);
            for (Field field : targetClass.getDeclaredFields()) {
                field.setAccessible(true);
                String value = rawFields.get(field.getName());
                if (value == null) {
                    if (field.isAnnotationPresent(Required.class)) {
                        Required req = field.getAnnotation(Required.class);
                        throw new SquadPlanException(
                            "Required field '" + field.getName() + "' missing: " + req.message());
                    }
                    continue;
                }
                setField(instance, field, value);
            }
            SquadPlan ann = targetClass.getAnnotation(SquadPlan.class);
            if (ann.validate()) validate(instance, targetClass);
            return instance;
        } catch (SquadPlanException e) {
            throw e;
        } catch (Exception e) {
            throw new SquadPlanException(
                "Failed to deserialise " + targetClass.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public static String buildSchemaPrompt(Class<?> targetClass) {
        SquadPlan ann = targetClass.getAnnotation(SquadPlan.class);
        String desc = (ann != null && !ann.description().isEmpty())
            ? ann.description() : targetClass.getSimpleName();
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nYou MUST respond with ONLY valid JSON matching this schema.\n");
        sb.append("Do not include any explanation or text outside the JSON.\n");
        sb.append("Schema for ").append(desc).append(":\n{\n");
        Field[] fields = targetClass.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            Field f = fields[i];
            boolean req = f.isAnnotationPresent(Required.class);
            sb.append("  \"").append(f.getName()).append("\": ").append(describeType(f));
            if (req) sb.append("  // REQUIRED");
            if (i < fields.length - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T> void setField(T instance, Field field, String value)
            throws IllegalAccessException {
        Class<?> type = field.getType();
        if (type == String.class) field.set(instance, value);
        else if (type == int.class || type == Integer.class)
            field.set(instance, Integer.parseInt(value.trim()));
        else if (type == long.class || type == Long.class)
            field.set(instance, Long.parseLong(value.trim()));
        else if (type == boolean.class || type == Boolean.class)
            field.set(instance, Boolean.parseBoolean(value.trim()));
        else if (type == List.class)
            field.set(instance, parseJsonArray(value));
        else
            field.set(instance, value);
    }

    public static Map<String, String> parseJsonFields(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        String inner = json.trim();
        if (inner.startsWith("{")) inner = inner.substring(1);
        if (inner.endsWith("}")) inner = inner.substring(0, inner.length() - 1);
        int i = 0;
        while (i < inner.length()) {
            while (i < inner.length() && " \n\r,\t".indexOf(inner.charAt(i)) >= 0) i++;
            if (i >= inner.length()) break;
            if (inner.charAt(i) != '"') { i++; continue; }
            int keyEnd = inner.indexOf('"', i + 1);
            if (keyEnd < 0) break;
            String key = inner.substring(i + 1, keyEnd);
            i = keyEnd + 1;
            while (i < inner.length() && inner.charAt(i) != ':') i++;
            i++;
            while (i < inner.length() && " \n\r\t".indexOf(inner.charAt(i)) >= 0) i++;
            if (i >= inner.length()) break;
            char c = inner.charAt(i);
            String value;
            if (c == '"') {
                int start = i + 1, end = start;
                while (end < inner.length()) {
                    if (inner.charAt(end) == '\\') { end += 2; continue; }
                    if (inner.charAt(end) == '"') break;
                    end++;
                }
                value = inner.substring(start, end);
                i = end + 1;
            } else if (c == '[') {
                int depth = 0, start = i;
                while (i < inner.length()) {
                    if (inner.charAt(i) == '[') depth++;
                    else if (inner.charAt(i) == ']') { depth--; if (depth == 0) { i++; break; } }
                    i++;
                }
                value = inner.substring(start, i);
            } else {
                int start = i;
                while (i < inner.length() && inner.charAt(i) != ',' && inner.charAt(i) != '}') i++;
                value = inner.substring(start, i).trim();
            }
            result.put(key, value);
        }
        return result;
    }

    public static List<String> parseJsonArray(String json) {
        List<String> result = new ArrayList<>();
        String inner = json.trim();
        if (inner.startsWith("[")) inner = inner.substring(1);
        if (inner.endsWith("]")) inner = inner.substring(0, inner.length() - 1);
        int i = 0;
        while (i < inner.length()) {
            while (i < inner.length() && " ,\n\r".indexOf(inner.charAt(i)) >= 0) i++;
            if (i >= inner.length()) break;
            if (inner.charAt(i) == '"') {
                int start = i + 1, end = start;
                while (end < inner.length()) {
                    if (inner.charAt(end) == '\\') { end += 2; continue; }
                    if (inner.charAt(end) == '"') break;
                    end++;
                }
                result.add(inner.substring(start, end));
                i = end + 1;
            } else {
                int start = i;
                while (i < inner.length() && inner.charAt(i) != ',') i++;
                String v = inner.substring(start, i).trim();
                if (!v.isEmpty()) result.add(v);
            }
        }
        return result;
    }

    private static <T> void validate(T instance, Class<T> cls) throws IllegalAccessException {
        for (Field field : cls.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Required.class)) continue;
            field.setAccessible(true);
            Object val = field.get(instance);
            if (val == null) {
                Required req = field.getAnnotation(Required.class);
                throw new SquadPlanException(
                    "Validation failed: '" + field.getName() + "' — " + req.message());
            }
            if (val instanceof String && ((String) val).isBlank())
                throw new SquadPlanException("Required field '" + field.getName() + "' is blank");
            if (val instanceof List && ((List<?>) val).isEmpty())
                throw new SquadPlanException("Required field '" + field.getName() + "' is empty");
        }
    }

    private static String stripCodeFences(String text) {
        if (text.startsWith("```")) {
            int nl = text.indexOf('\n');
            if (nl > 0) text = text.substring(nl + 1);
            int last = text.lastIndexOf("```");
            if (last > 0) text = text.substring(0, last).trim();
        }
        return text;
    }

    private static String describeType(Field field) {
        Class<?> type = field.getType();
        if (type == String.class)  return "\"string\"";
        if (type == int.class || type == Integer.class) return "0";
        if (type == boolean.class || type == Boolean.class) return "true";
        if (type == List.class)    return "[\"item1\", \"item2\"]";
        return "\"...\"";
    }
}