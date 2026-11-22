package com.mesutpiskin.keycloak.auth.email.ds.idp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.utils.KeycloakSessionUtil;

/**
 * Records the status of a failed outbound call. The response body is copied
 * only as Keycloak reads it, so token exchange and profile fetch behave as before.
 */
final class DsIdpUpstreamCapture implements org.apache.http.HttpResponseInterceptor {

    static final DsIdpUpstreamCapture INSTANCE = new DsIdpUpstreamCapture();

    private DsIdpUpstreamCapture() {
    }

    @Override
    public void process(HttpResponse response, HttpContext context) {
        try {
            capture(response, context);
        } catch (RuntimeException ex) {
            // A diagnosis hook must not change the identity-provider call.
        }
    }

    private static void capture(HttpResponse response, HttpContext context) {
        if (response == null || response.getStatusLine() == null) {
            return;
        }
        int status = response.getStatusLine().getStatusCode();
        if (status < 400) {
            return;
        }
        KeycloakSession session = currentSession();
        if (session == null) {
            return;
        }
        HttpRequest request = context == null ? null : (HttpRequest) context.getAttribute(HttpCoreContext.HTTP_REQUEST);
        HttpHost host = context == null ? null : (HttpHost) context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST);
        String url = DsIdpStages.withoutSecrets(absoluteUrl(request, host));
        DsIdpCall call = new DsIdpCall(url, status, charset(response.getEntity()));
        HttpEntity entity = response.getEntity();
        if (entity != null && looksTextual(entity)) {
            response.setEntity(new CapturingEntity(entity, call));
        }
        DsIdpCall.remember(session, call);
    }

    private static KeycloakSession currentSession() {
        try {
            return KeycloakSessionUtil.getKeycloakSession();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    static String absoluteUrl(HttpRequest request, HttpHost host) {
        if (request == null || request.getRequestLine() == null || request.getRequestLine().getUri() == null) {
            return "";
        }
        String uri = request.getRequestLine().getUri();
        if (uri.startsWith("https://") || uri.startsWith("http://")) {
            return uri;
        }
        if (host == null) {
            return uri;
        }
        String path = uri.startsWith("/") ? uri : "/" + uri;
        return host.toURI() + path;
    }

    private static Charset charset(HttpEntity entity) {
        if (entity == null || entity.getContentType() == null || entity.getContentType().getValue() == null) {
            return StandardCharsets.UTF_8;
        }
        String header = entity.getContentType().getValue().toLowerCase(java.util.Locale.ROOT);
        int marker = header.indexOf("charset=");
        if (marker < 0) {
            return StandardCharsets.UTF_8;
        }
        String name = header.substring(marker + "charset=".length()).trim();
        int end = name.indexOf(';');
        if (end >= 0) {
            name = name.substring(0, end).trim();
        }
        name = name.replace("\"", "");
        try {
            return Charset.forName(name);
        } catch (RuntimeException ex) {
            return StandardCharsets.UTF_8;
        }
    }

    private static boolean looksTextual(HttpEntity entity) {
        Header type = entity.getContentType();
        if (type == null || type.getValue() == null) {
            return true;
        }
        String value = type.getValue().toLowerCase(java.util.Locale.ROOT);
        return value.contains("json") || value.contains("text") || value.contains("xml")
                || value.contains("javascript") || value.contains("x-www-form-urlencoded");
    }

    private static final class CapturingEntity extends HttpEntityWrapper {

        private final DsIdpCall call;

        private CapturingEntity(HttpEntity wrapped, DsIdpCall call) {
            super(wrapped);
            this.call = call;
        }

        @Override
        public InputStream getContent() throws IOException {
            return new CapturingStream(super.getContent(), call);
        }

        @Override
        public void writeTo(OutputStream outStream) throws IOException {
            try (InputStream in = getContent()) {
                in.transferTo(outStream);
            }
        }
    }

    private static final class CapturingStream extends InputStream {

        private final InputStream delegate;
        private final DsIdpCall call;

        private CapturingStream(InputStream delegate, DsIdpCall call) {
            this.delegate = delegate;
            this.call = call;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                call.offer(new byte[] {(byte) value}, 0, 1);
            } else {
                call.finish();
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                call.offer(buffer, offset, count);
            } else if (count < 0) {
                call.finish();
            }
            return count;
        }

        @Override
        public void close() throws IOException {
            call.finish();
            delegate.close();
        }
    }
}
