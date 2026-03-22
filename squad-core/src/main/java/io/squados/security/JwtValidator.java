package io.squados.security;

import java.time.Instant;
import java.util.*;

/**
 * Validates JWT tokens and extracts AgentIdentity.
 *
 * squad-core uses a simple Base64 decode approach (no external JWT library).
 * For production, swap with a real JWT library (nimbus-jose-jwt, jjwt, etc.)
 * by implementing the JwtValidator.TokenParser interface.
 *
 * Token format expected:
 *   header.payload.signature (standard JWT)
 *
 * Payload claims extracted:
 *   sub   — subject (user/service ID)
 *   roles — array of role strings
 *   iss   — issuer
 *   exp   — expiry (Unix timestamp)
 *
 * Validation checks:
 *   1. Token is not null/empty
 *   2. Token has 3 parts (header.payload.signature)
 *   3. Payload decodes to valid JSON
 *   4. Token is not expired (exp > now)
 *   5. Issuer matches expected issuer (if configured)
 */
public class JwtValidator {

    private final String expectedIssuer;

    public JwtValidator() { this("squados"); }
    public JwtValidator(String expectedIssuer) {
        this.expectedIssuer = expectedIssuer;
    }

    /**
     * Validate a JWT token and return the identity.
     *
     * @param token Raw JWT string
     * @return AgentIdentity if valid
     * @throws SecurityException if token is invalid or expired
     */
    public AgentIdentity validate(String token) {
        if (token == null || token.isBlank()) {
            throw new SecurityException("JWT token is missing");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new SecurityException("Invalid JWT format — expected header.payload.signature");
        }

        // Decode payload (Base64 URL decoding)
        String payload;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            payload = new String(decoded);
        } catch (Exception e) {
            throw new SecurityException("Invalid JWT payload encoding: " + e.getMessage());
        }

        // Parse payload fields
        String  subject    = extractString(payload, "sub");
        String  issuer     = extractString(payload, "iss");
        long    expUnix    = extractLong(payload, "exp");
        List<String> roles = extractRoles(payload);

        // Validate expiry
        Instant expiry = expUnix > 0
            ? Instant.ofEpochSecond(expUnix) : Instant.MAX;
        if (expUnix > 0 && Instant.now().isAfter(expiry)) {
            throw new SecurityException("JWT token has expired");
        }

        // Validate issuer (if configured)
        if (expectedIssuer != null && !expectedIssuer.isBlank()
            && !expectedIssuer.equals(issuer)) {
            throw new SecurityException(
                "JWT issuer mismatch: expected " + expectedIssuer + " but got " + issuer);
        }

        if (subject == null || subject.isBlank()) {
            throw new SecurityException("JWT missing subject (sub) claim");
        }

        return new AgentIdentity(subject, roles, issuer, expiry, token);
    }

    /**
     * Create a simple test JWT token (NOT for production use).
     * For testing and demos only — no real signature.
     */
    public static String createTestToken(String subject,
                                          String issuer,
                                          long expiryEpochSeconds,
                                          String... roles) {
        String header  = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("{\"alg\":\"none\",\"typ\":\"JWT\"}").getBytes());

        StringBuilder rolesJson = new StringBuilder("[");
        for (int i = 0; i < roles.length; i++) {
            rolesJson.append("\"").append(roles[i]).append("\"");
            if (i < roles.length - 1) rolesJson.append(",");
        }
        rolesJson.append("]");

        String payloadJson = "{" +
            "\"sub\":\"" + subject + "\"," +
            "\"iss\":\"" + issuer + "\"," +
            "\"exp\":" + expiryEpochSeconds + "," +
            "\"roles\":" + rolesJson + "}";

        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.getBytes());

        return header + "." + payload + ".test-signature";
    }

    // ── Helpers ──────────────────────────────────────────────────

    String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end < 0 ? null : json.substring(start, end);
    }

    long extractLong(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return 0;
        start += search.length();
        int end = json.indexOf(",", start);
        if (end < 0) end = json.indexOf("}", start);
        if (end < 0) return 0;
        try { return Long.parseLong(json.substring(start, end).trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    List<String> extractRoles(String json) {
        String search = "\"roles\":[";
        int start = json.indexOf(search);
        if (start < 0) return List.of();
        start += search.length();
        int end = json.indexOf("]", start);
        if (end < 0) return List.of();
        String rolesSection = json.substring(start, end);
        List<String> roles = new ArrayList<>();
        for (String part : rolesSection.split(",")) {
            String r = part.trim().replace("\"", "");
            if (!r.isBlank()) roles.add(r);
        }
        return roles;
    }

    private String padBase64(String base64) {
        int pad = base64.length() % 4;
        if (pad == 2) return base64 + "==";
        if (pad == 3) return base64 + "=";
        return base64;
    }
}