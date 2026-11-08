package com.mesutpiskin.keycloak.auth.email.ds;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Cookie;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.FormContext;
import org.keycloak.sessions.AuthenticationSessionModel;

/** Cloudflare Turnstile. The secret stays in the Keycloak process environment. */
final class Turnstile {
    static final String LOGIN = "login";
    static final String EMAIL = "email";
    static final String RESET = "reset";
    static final String OTP = "otp";
    static final String REGISTER = "register";
    static final String REJECTED = "Confirm you are a person and try again.";
    static final String BYPASS_PARAM = "turnstile_bypass";
    static final String BYPASS_HEADER = "X-Turnstile-Bypass";
    private static final String BYPASS_NOTE = "ds-turnstile-bypass";

    private static final Logger LOG = Logger.getLogger(Turnstile.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static final String SITEVERIFY = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private static String siteKeyFor(org.keycloak.http.HttpRequest request, AuthenticationSessionModel session) {
        return bypassed(request, session) ? "" : siteKey();
    }

    private Turnstile() {
    }

    static String siteKey() {
        return env("TURNSTILE_KEY");
    }

    static boolean configured() {
        return !siteKey().isBlank() && !env("TURNSTILE_SECRET").isBlank();
    }

    /** Public site key, or empty when this authentication session is bypassed. */
    static String siteKeyFor(AuthenticationFlowContext context) {
        return siteKeyFor(context.getHttpRequest(), context.getAuthenticationSession());
    }

    /** Public site key, or empty when this authentication session is bypassed. */
    static String siteKeyFor(FormContext context) {
        return siteKeyFor(context.getHttpRequest(), context.getAuthenticationSession());
    }

    /**
     * @return true when the token is valid, Turnstile is not configured, or the bypass secret was presented
     */
    static boolean allows(AuthenticationFlowContext context, String token, String action) {
        if (bypassed(context.getHttpRequest(), context.getAuthenticationSession())) {
            return true;
        }
        return allows(token, context.getConnection().getRemoteAddr(), action);
    }

    /**
     * @return true when the token is valid, Turnstile is not configured, or the bypass secret was presented
     */
    static boolean allows(FormContext context, String token, String action) {
        if (bypassed(context.getHttpRequest(), context.getAuthenticationSession())) {
            return true;
        }
        return allows(token, context.getConnection().getRemoteAddr(), action);
    }

    /**
     * @return true when the token is valid, or when Turnstile is not configured yet
     */
    static boolean allows(String token, String remoteIp, String action) {
        if (!configured()) {
            if (WARNED.compareAndSet(false, true)) {
                LOG.warn("Turnstile is not configured. Login and registration are not checking it.");
            }
            return true;
        }
        if (token == null || token.isBlank() || token.length() > 2048) {
            return false;
        }
        try {
            StringBuilder form = new StringBuilder();
            form.append("secret=").append(encode(env("TURNSTILE_SECRET")));
            form.append("&response=").append(encode(token));
            if (remoteIp != null && !remoteIp.isBlank()) {
                form.append("&remoteip=").append(encode(remoteIp));
            }
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(SITEVERIFY))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(form.toString()))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                LOG.warn("Turnstile siteverify failed: HTTP " + response.statusCode());
                return false;
            }
            JsonNode body = JSON.readTree(response.body());
            if (!accepted(body, action, hostnames())) {
                JsonNode codes = body.path("error-codes");
                if (codes.isArray() && !codes.isEmpty()) {
                    LOG.warn("Turnstile siteverify rejected: " + codes);
                } else {
                    LOG.warn("Turnstile siteverify rejected the action or hostname");
                }
                return false;
            }
            return true;
        } catch (Exception ex) {
            LOG.warn("Turnstile siteverify failed");
            return false;
        }
    }

    static boolean matches(String expected, String presented) {
        if (expected == null || expected.isBlank() || presented == null) {
            return false;
        }
        String got = presented.trim();
        if (got.isEmpty() || got.length() > 512) {
            return false;
        }
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = got.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            MessageDigest.isEqual(left, left);
            return false;
        }
        return MessageDigest.isEqual(left, right);
    }

    /** A blank server secret never bypasses, including when an older session note is still set. */
    static boolean bypasses(String expected, boolean noted, String presented) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        return noted || matches(expected, presented);
    }

    private static boolean bypassed(org.keycloak.http.HttpRequest request, AuthenticationSessionModel session) {
        String expected = env("TURNSTILE_BYPASS");
        if (expected.isBlank()) {
            return false;
        }
        if (session != null && "1".equals(session.getAuthNote(BYPASS_NOTE))) {
            return true;
        }
        if (request != null && presented(request, expected)) {
            if (session != null) {
                session.setAuthNote(BYPASS_NOTE, "1");
            }
            return true;
        }
        return false;
    }

    private static boolean presented(org.keycloak.http.HttpRequest request, String expected) {
        return matches(expected, firstQuery(request))
                || matches(expected, firstHeader(request))
                || matches(expected, firstCookie(request))
                || matches(expected, firstForm(request));
    }

    private static String firstQuery(org.keycloak.http.HttpRequest request) {
        if (request.getUri() == null || request.getUri().getQueryParameters() == null) {
            return null;
        }
        return request.getUri().getQueryParameters().getFirst(BYPASS_PARAM);
    }

    private static String firstHeader(org.keycloak.http.HttpRequest request) {
        if (request.getHttpHeaders() == null) {
            return null;
        }
        return request.getHttpHeaders().getHeaderString(BYPASS_HEADER);
    }

    private static String firstCookie(org.keycloak.http.HttpRequest request) {
        if (request.getHttpHeaders() == null) {
            return null;
        }
        Map<String, Cookie> cookies = request.getHttpHeaders().getCookies();
        if (cookies == null) {
            return null;
        }
        Cookie cookie = cookies.get(BYPASS_PARAM);
        return cookie == null ? null : cookie.getValue();
    }

    private static String firstForm(org.keycloak.http.HttpRequest request) {
        if (request.getHttpMethod() == null || !"POST".equalsIgnoreCase(request.getHttpMethod())) {
            return null;
        }
        if (request.getDecodedFormParameters() == null) {
            return null;
        }
        return request.getDecodedFormParameters().getFirst(BYPASS_PARAM);
    }

    static boolean accepted(JsonNode body, String action, Set<String> hostnames) {
        return body.path("success").asBoolean(false)
                && action.equals(body.path("action").asText(""))
                && hostnames.contains(body.path("hostname").asText(""));
    }

    static Set<String> hostnames() {
        String raw = env("TURNSTILE_HOSTNAMES");
        if (raw.isBlank()) {
            return Set.of("auth.dobbersports.com");
        }
        Set<String> hosts = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String host = part.trim();
            if (!host.isEmpty()) {
                hosts.add(host);
            }
        }
        return hosts.isEmpty() ? Set.of("auth.dobbersports.com") : Set.copyOf(hosts);
    }

    private static String env(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
