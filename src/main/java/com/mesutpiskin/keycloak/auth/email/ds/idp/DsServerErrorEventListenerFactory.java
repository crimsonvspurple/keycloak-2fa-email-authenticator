package com.mesutpiskin.keycloak.auth.email.ds.idp;

import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.PostMigrationEvent;

/**
 * Enables this listener on every realm after migration. Existing listeners,
 * including jboss-logging, are left in place.
 */
public final class DsServerErrorEventListenerFactory implements EventListenerProviderFactory {

    public static final String PROVIDER_ID = "ds-server-errors";

    private static final Logger LOG = Logger.getLogger(DsServerErrorEventListenerFactory.class);

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new DsServerErrorEventListener();
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        factory.register(event -> {
            if (event instanceof PostMigrationEvent post) {
                try {
                    KeycloakModelUtils.runJobInTransaction(post.getFactory(), this::ensureEnabled);
                } catch (RuntimeException ex) {
                    LOG.error("Could not enable the ds-server-errors listener");
                }
            }
        });
    }

    private void ensureEnabled(KeycloakSession session) {
        session.realms().getRealmsStream().forEach(this::ensureEnabled);
    }

    private void ensureEnabled(RealmModel realm) {
        Set<String> listeners = new LinkedHashSet<>(realm.getEventsListenersStream().toList());
        if (listeners.add(PROVIDER_ID)) {
            realm.setEventsListeners(listeners);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
