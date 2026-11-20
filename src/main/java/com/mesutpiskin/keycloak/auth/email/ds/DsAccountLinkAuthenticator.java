package com.mesutpiskin.keycloak.auth.email.ds;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Runs before create-if-unique. Manage account already has a signed-in user,
 * and the new identity attaches to that user. A normal Google or X login has
 * no signed-in user, so this step is skipped.
 */
public class DsAccountLinkAuthenticator extends AbstractIdpAuthenticator {

    @Override
    protected void authenticateImpl(AuthenticationFlowContext context, SerializedBrokeredIdentityContext serializedCtx, BrokeredIdentityContext brokerContext) {
        UserModel signedIn = context.getAuthenticationSession().getAuthenticatedUser();
        if (!DsBrokerLink.linkSignedInAccount(signedIn == null ? null : signedIn.getId())) {
            context.attempted();
            return;
        }
        context.setUser(signedIn);
        context.success();
    }

    @Override
    protected void actionImpl(AuthenticationFlowContext context, SerializedBrokeredIdentityContext serializedCtx, BrokeredIdentityContext brokerContext) {
        authenticateImpl(context, serializedCtx, brokerContext);
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }
}
