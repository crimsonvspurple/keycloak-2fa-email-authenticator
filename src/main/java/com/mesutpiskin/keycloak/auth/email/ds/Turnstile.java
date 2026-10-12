package com.mesutpiskin.keycloak.auth.email.ds;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

/** Cloudflare Turnstile. The secret stays in the Keycloak process environment. */
final class Turnstile {
    static final String LOGIN = "login";
    static final String EMAIL = "email";
    static final String RESET = "reset";
    static final String OTP = "otp";
    static final String REGISTER = "register";
    static final String REJECTED = "Confirm you are a person and try again.";

    private static final Logger LOG = Logger.getLogger(Turnstile.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static final String SITEVERIFY = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private Turnstile() {
    }

    static String siteKey() {
        return env("TURNSTILE_KEY");
    }

    static boolean configured() {
        return !siteKey().isBlank() && !env("TURNSTILE_SECRET").isBlank();
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
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SITEVERIFY))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
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
