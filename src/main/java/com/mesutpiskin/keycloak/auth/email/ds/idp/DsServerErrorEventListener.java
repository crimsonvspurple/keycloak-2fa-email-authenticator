package com.mesutpiskin.keycloak.auth.email.ds.idp;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;

/**
 * Writes server-problem Keycloak events to {@link DsServerErrorEvents#CATEGORY}.
 * User mistakes stay on the normal console log only.
 */
public final class DsServerErrorEventListener implements EventListenerProvider {

    @Override
    public void onEvent(Event event) {
        DsServerErrorEvents.log(event);
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        if (event == null || event.getError() == null) {
            return;
        }
        String realm = event.getAuthDetails() == null ? null : event.getAuthDetails().getRealmName();
        String client = event.getAuthDetails() == null ? null : event.getAuthDetails().getClientId();
        String operation = event.getOperationType() == null ? null : event.getOperationType().name();
        DsServerErrorEvents.logAdmin(operation, realm, client, event.getError(), event.getResourcePath());
    }

    @Override
    public void close() {
    }
}
