package com.mesutpiskin.keycloak.auth.email.ds.idp;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.keycloak.models.KeycloakSession;

/**
 * One failed upstream response seen during the current Keycloak request.
 * The body is filled as Keycloak reads it, and only the first 8KB is kept.
 */
public final class DsIdpCall {

    static final String SESSION_KEY = "ds.idp.upstream";

    private final String url;
    private final int status;
    private final Charset charset;
    private final byte[] captured = new byte[DsIdpSnippets.CAPTURE_LIMIT];
    private int capturedLength;
    private boolean finished;

    DsIdpCall(String url, int status, Charset charset) {
        this.url = url == null ? "" : url;
        this.status = status;
        this.charset = charset == null ? StandardCharsets.UTF_8 : charset;
    }

    public String url() {
        return url;
    }

    public int status() {
        return status;
    }

    public String body() {
        finish();
        if (capturedLength == 0) {
            return "";
        }
        return new String(captured, 0, capturedLength, charset);
    }

    void offer(byte[] data, int offset, int length) {
        if (finished || data == null || length <= 0 || capturedLength >= captured.length) {
            return;
        }
        int room = captured.length - capturedLength;
        int take = Math.min(room, length);
        System.arraycopy(data, offset, captured, capturedLength, take);
        capturedLength += take;
    }

    void finish() {
        finished = true;
    }

    static void remember(KeycloakSession session, DsIdpCall call) {
        if (session == null || call == null) {
            return;
        }
        Object existing = session.getAttribute(SESSION_KEY);
        List<DsIdpCall> calls;
        if (existing instanceof List<?> list) {
            calls = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof DsIdpCall recorded) {
                    calls.add(recorded);
                }
            }
        } else {
            calls = new ArrayList<>();
        }
        if (calls.size() >= 8) {
            calls.remove(0);
        }
        calls.add(call);
        session.setAttribute(SESSION_KEY, List.copyOf(calls));
    }

    static List<DsIdpCall> recorded(KeycloakSession session) {
        if (session == null) {
            return List.of();
        }
        Object existing = session.getAttribute(SESSION_KEY);
        if (!(existing instanceof List<?> list)) {
            return List.of();
        }
        List<DsIdpCall> calls = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof DsIdpCall recorded) {
                calls.add(recorded);
            }
        }
        return calls;
    }
}
