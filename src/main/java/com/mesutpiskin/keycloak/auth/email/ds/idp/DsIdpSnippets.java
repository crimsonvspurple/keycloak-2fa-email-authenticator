package com.mesutpiskin.keycloak.auth.email.ds.idp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Turns an upstream error body into a short diagnosis line. Tokens, codes,
 * secrets, and email addresses are removed before the line is truncated.
 */
public final class DsIdpSnippets {

    public static final int MAX_CHARS = 300;
    static final int CAPTURE_LIMIT = 8192;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9\\-._~+/]+=*");
    private static final Pattern ASSIGNED_SECRET = Pattern.compile(
            "(?i)\\b(access_token|refresh_token|id_token|client_secret|code|password|secret|assertion|authorization)\\b\\s*[=:]\\s*(\"[^\"]*\"|[^\\s&,}]+)");
    private static final Pattern LONG_TOKEN = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z0-9\\-._~+/]{32,}={0,2}(?![A-Za-z0-9])");

    private DsIdpSnippets() {
    }

    public static String snippet(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String trimmed = body.strip();
        if (trimmed.startsWith("<")) {
            return "";
        }
        String extracted = trimmed.startsWith("{") || trimmed.startsWith("[")
                ? fromJson(trimmed)
                : collapse(redact(trimmed));
        if (extracted.isBlank()) {
            extracted = collapse(redact(trimmed));
        }
        extracted = extracted.replace("\"", "'").replaceAll("[\\r\\n]+", " ").strip();
        if (controlChars(extracted) > 0) {
            return "";
        }
        if (extracted.length() > MAX_CHARS) {
            return extracted.substring(0, MAX_CHARS);
        }
        return extracted;
    }

    private static String fromJson(String body) {
        try {
            JsonNode root = JSON.readTree(body);
            List<String> parts = new ArrayList<>();
            if (root.isArray() && !root.isEmpty()) {
                root = root.get(0);
            }
            add(parts, "title", text(root, "title"));
            add(parts, "type", text(root, "type"));
            add(parts, "error", text(root, "error"));
            add(parts, "status", text(root, "status"));
            add(parts, "detail", firstText(root, "detail", "message", "error_description"));
            JsonNode errors = root.get("errors");
            if (errors != null && errors.isArray() && !errors.isEmpty()) {
                JsonNode first = errors.get(0);
                add(parts, "title", text(first, "title"));
                add(parts, "detail", firstText(first, "detail", "message"));
                String code = text(first, "code");
                if (code != null && code.chars().allMatch(Character::isDigit) && code.length() <= 6) {
                    add(parts, "code", code);
                }
            }
            if (parts.isEmpty()) {
                return "";
            }
            return String.join(" ", parts);
        } catch (Exception ex) {
            return "";
        }
    }

    private static void add(List<String> parts, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String safe = collapse(redact(value));
        if (safe.isBlank() || "[redacted]".equals(safe)) {
            return;
        }
        String rendered = key + "=" + safe;
        for (String existing : parts) {
            if (existing.equals(rendered) || existing.startsWith(key + "=")) {
                return;
            }
        }
        parts.add(rendered);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isContainerNode()) {
            return null;
        }
        return value.asText();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    static String redact(String value) {
        String safe = value;
        safe = JWT.matcher(safe).replaceAll("[redacted]");
        safe = EMAIL.matcher(safe).replaceAll("[email]");
        safe = BEARER.matcher(safe).replaceAll("Bearer [redacted]");
        safe = ASSIGNED_SECRET.matcher(safe).replaceAll("$1=[redacted]");
        safe = LONG_TOKEN.matcher(safe).replaceAll("[redacted]");
        return safe;
    }

    private static String collapse(String value) {
        return value.replaceAll("\\s+", " ").strip();
    }

    private static int controlChars(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                count++;
            }
        }
        return count;
    }

    public static String safeToken(String value, int max) {
        if (value == null) {
            return "-";
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9._-]", "");
        if (cleaned.isEmpty()) {
            return "-";
        }
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    public static String oneLine(String value, int max) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]+", " ").replace("\"", "'").strip();
        if (cleaned.length() > max) {
            return cleaned.substring(0, max);
        }
        return cleaned;
    }

    public static String html(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static String sentence(String provider) {
        String name = oneLine(provider, 80);
        if (name.isBlank()) {
            return "We couldn't sign you in with this service. Your account may be suspended or restricted, "
                    + "or the service is temporarily unavailable. Please try another sign-in method.";
        }
        return "We couldn't sign you in with " + name + ". Your " + name
                + " account may be suspended or restricted, or " + name
                + " is temporarily unavailable. Please try another sign-in method.";
    }

    public static String displayName(String alias, String configured) {
        String fromAdmin = oneLine(configured, 80);
        if (!fromAdmin.isBlank()) {
            return fromAdmin;
        }
        if (alias == null) {
            return "";
        }
        return switch (alias.toLowerCase(Locale.ROOT)) {
            case "x", "twitter" -> "X";
            case "google" -> "Google";
            case "linkedin", "linkedin-openid-connect" -> "LinkedIn";
            case "apple" -> "Apple";
            default -> oneLine(alias, 80);
        };
    }
}
