package com.mesutpiskin.keycloak.auth.email.ds.idp;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MediaType;

import org.keycloak.headers.DefaultSecurityHeadersProvider;
import org.keycloak.models.KeycloakSession;

/**
 * Keeps Keycloak's browser security headers, then replaces a social broker
 * error so the ds theme is what the browser receives.
 */
public final class DsSecurityHeadersProvider extends DefaultSecurityHeadersProvider {

    private static final String HANDLED = "ds.idp.handled";

    private final KeycloakSession session;

    public DsSecurityHeadersProvider(KeycloakSession session) {
        super(session);
        this.session = session;
    }

    @Override
    public void addHeaders(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        restyle(requestContext, responseContext);
        super.addHeaders(requestContext, responseContext);
    }

    private void restyle(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        try {
            if (requestContext.getProperty(HANDLED) != null || session.getContext() == null
                    || session.getContext().getUri() == null) {
                return;
            }
            String path = session.getContext().getUri().getPath();
            int status = responseContext.getStatus();
            if (DsBrokerFailures.parse(path) == null || !DsBrokerFailures.isFailureStatus(status)) {
                return;
            }
            requestContext.setProperty(HANDLED, Boolean.TRUE);
            MediaType type = responseContext.getMediaType();
            Object entity = responseContext.getEntity();
            DsBrokerFailures.logAndRestyle(session, path, status, type, entity, (nextStatus, html) -> {
                responseContext.setStatus(nextStatus);
                responseContext.setEntity(html, null, MediaType.TEXT_HTML_TYPE.withCharset("UTF-8"));
            });
        } catch (RuntimeException ex) {
            // Security headers still have to be written when restyling fails.
        }
    }
}
