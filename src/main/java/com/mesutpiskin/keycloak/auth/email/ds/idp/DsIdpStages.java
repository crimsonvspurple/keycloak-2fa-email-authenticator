package com.mesutpiskin.keycloak.auth.email.ds.idp;

import java.net.URI;
import java.util.Locale;

/**
 * Classifies an upstream call as token exchange, profile fetch, or other.
 * Comparison ignores the query string so authorization codes are never kept.
 */
public final class DsIdpStages {

    public static final String TOKEN_EXCHANGE = "token exchange";
    public static final String PROFILE_FETCH = "profile fetch";
    public static final String OTHER = "other";

    private DsIdpStages() {
    }

    public static String stage(String requestUrl, String tokenUrl, String userInfoUrl) {
        if (sameEndpoint(requestUrl, tokenUrl)) {
            return TOKEN_EXCHANGE;
        }
        if (sameEndpoint(requestUrl, userInfoUrl)) {
            return PROFILE_FETCH;
        }
        String path = pathOf(requestUrl);
        if (path.endsWith("/token") || path.endsWith("/access_token") || path.contains("/oauth2/token")
                || path.contains("/oauth/token")) {
            return TOKEN_EXCHANGE;
        }
        if (path.contains("userinfo") || path.endsWith("/users/me") || path.endsWith("/me") || path.endsWith("/user")) {
            return PROFILE_FETCH;
        }
        return OTHER;
    }

    public static String withoutSecrets(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String candidate = raw.strip();
        int query = candidate.indexOf('?');
        int fragment = candidate.indexOf('#');
        int cut = candidate.length();
        if (query >= 0) {
            cut = Math.min(cut, query);
        }
        if (fragment >= 0) {
            cut = Math.min(cut, fragment);
        }
        candidate = candidate.substring(0, cut);
        try {
            URI uri = URI.create(candidate);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return candidate;
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            if (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            StringBuilder kept = new StringBuilder();
            kept.append(uri.getScheme().toLowerCase(Locale.ROOT)).append("://");
            kept.append(uri.getHost().toLowerCase(Locale.ROOT));
            int port = uri.getPort();
            if (port != -1 && !isDefaultPort(uri.getScheme(), port)) {
                kept.append(':').append(port);
            }
            kept.append(path);
            return kept.toString();
        } catch (IllegalArgumentException ex) {
            return candidate;
        }
    }

    static boolean sameEndpoint(String left, String right) {
        String a = withoutSecrets(left);
        String b = withoutSecrets(right);
        return !a.isBlank() && a.equalsIgnoreCase(b);
    }

    private static String pathOf(String url) {
        String stripped = withoutSecrets(url).toLowerCase(Locale.ROOT);
        int scheme = stripped.indexOf("://");
        int path = stripped.indexOf('/', scheme < 0 ? 0 : scheme + 3);
        if (path < 0) {
            return stripped;
        }
        return stripped.substring(path);
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("https".equalsIgnoreCase(scheme) && port == 443)
                || ("http".equalsIgnoreCase(scheme) && port == 80);
    }
}
