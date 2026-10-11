package com.mesutpiskin.keycloak.auth.email.ds;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import jakarta.ws.rs.core.MultivaluedMap;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * One-time codes and same-browser links. Keycloak checks them. Notifier only delivers the text.
 * The link continues the authentication session that is already open in this browser.
 */
final class DsAuthCodes {

    static final String CODE_HASH = "ds.code";
    static final String MAGIC_HASH = "ds.magic";
    static final String EXPIRES = "ds.exp";
    static final String ATTEMPTS = "ds.attempts";
    static final long TTL_MS = 10 * 60 * 1000L;
    static final int MAX_ATTEMPTS = 5;

    private static final Logger LOG = Logger.getLogger(DsAuthCodes.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private DsAuthCodes() {
    }

    static boolean send(AuthenticationFlowContext context, String kind, String subject, String intro) {
        UserModel user = context.getUser();
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return false;
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        byte[] magicBytes = new byte[32];
        RANDOM.nextBytes(magicBytes);
        String magic = Base64.getUrlEncoder().withoutPadding().encodeToString(magicBytes);
        AuthenticationSessionModel session = context.getAuthenticationSession();
        session.setAuthNote(CODE_HASH, hex(sha256(code)));
        session.setAuthNote(MAGIC_HASH, hex(sha256(magic)));
        session.setAuthNote(EXPIRES, Long.toString(System.currentTimeMillis() + TTL_MS));
        session.setAuthNote(ATTEMPTS, "0");
        URI action = context.getActionUrl(context.generateAccessCode());
        String link = action.toString() + "&magic=" + magic;
        // Angle brackets stop Slack from cutting the link at the first &.
        String text = intro + "\n\n"
                + "Your code is " + code + ".\n"
                + "It expires in 10 minutes.\n\n"
                + "Or open this link in the same browser:\n<"
                + link + "|Sign in>\n";
        return post(kind, user.getEmail(), subject, text);
    }

    static boolean hasMagic(AuthenticationFlowContext context) {
        String magic = context.getHttpRequest().getUri().getQueryParameters().getFirst("magic");
        return magic != null && !magic.isBlank();
    }

    static boolean magicMatches(AuthenticationFlowContext context) {
        String magic = context.getHttpRequest().getUri().getQueryParameters().getFirst("magic");
        if (magic == null || magic.isBlank()) {
            return false;
        }
        if (!fresh(context)) {
            return false;
        }
        boolean ok = constantEquals(context.getAuthenticationSession().getAuthNote(MAGIC_HASH), hex(sha256(magic)));
        if (ok) {
            clear(context);
        }
        return ok;
    }

    /**
     * @return null when the code is accepted. The caller completes the step.
     */
    static String rejectCode(AuthenticationFlowContext context, String submitted) {
        String code = submitted == null ? "" : submitted.trim().replace(" ", "");
        if (!fresh(context)) {
            clear(context);
            return "That code expired. Email a new one.";
        }
        int attempts = attempts(context) + 1;
        context.getAuthenticationSession().setAuthNote(ATTEMPTS, Integer.toString(attempts));
        boolean ok = code.matches("\\d{6}")
                && constantEquals(context.getAuthenticationSession().getAuthNote(CODE_HASH), hex(sha256(code)));
        if (ok) {
            // The caller clears the notes after any extra checks, such as an optional password.
            return null;
        }
        if (attempts >= MAX_ATTEMPTS) {
            clear(context);
            return "Too many tries. Email a new code.";
        }
        return "That code is wrong.";
    }

    static String formValue(AuthenticationFlowContext context, String name) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        String value = form.getFirst(name);
        return value == null ? "" : value;
    }

    static boolean pressed(AuthenticationFlowContext context, String name) {
        return context.getHttpRequest().getDecodedFormParameters().containsKey(name);
    }

    private static boolean fresh(AuthenticationFlowContext context) {
        String exp = context.getAuthenticationSession().getAuthNote(EXPIRES);
        String hash = context.getAuthenticationSession().getAuthNote(CODE_HASH);
        if (exp == null || hash == null) {
            return false;
        }
        try {
            return System.currentTimeMillis() <= Long.parseLong(exp);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static int attempts(AuthenticationFlowContext context) {
        try {
            return Integer.parseInt(context.getAuthenticationSession().getAuthNote(ATTEMPTS));
        } catch (Exception ex) {
            return 0;
        }
    }

    static void clear(AuthenticationFlowContext context) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        session.removeAuthNote(CODE_HASH);
        session.removeAuthNote(MAGIC_HASH);
        session.removeAuthNote(EXPIRES);
        session.removeAuthNote(ATTEMPTS);
    }

    private static boolean post(String kind, String to, String subject, String text) {
        String url = System.getenv("DS_NOTIFIER_MAIL_URL");
        String token = System.getenv("MAIL_BRIDGE_TOKEN");
        if (url == null || url.isBlank() || token == null || token.isBlank()) {
            LOG.warn("Notifier mail is not configured");
            return false;
        }
        String body = "{\"kind\":" + json(kind)
                + ",\"to\":" + json(to)
                + ",\"subject\":" + json(subject)
                + ",\"text\":" + json(text) + "}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.trim()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Ds-Mail-Token", token.trim())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                LOG.warnf("Notifier mail failed status=%d", response.statusCode());
                return false;
            }
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warn("Notifier mail interrupted");
            return false;
        } catch (Exception ex) {
            LOG.warn("Notifier mail failed");
            return false;
        }
    }

    private static String json(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }

    private static boolean constantEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
