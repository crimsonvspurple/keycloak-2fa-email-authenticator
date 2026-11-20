package com.mesutpiskin.keycloak.auth.email.ds;

import jakarta.ws.rs.core.Response;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.events.Errors;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Replaces idp-auto-link. create-if-unique has already found the email.
 * The identity attaches only when that email is verified. An unverified
 * password account cannot receive a later Google or X login.
 */
public class DsVerifiedEmailLinkAuthenticator extends AbstractIdpAuthenticator {

    static final String UNVERIFIED = "This email is not verified yet. Sign in with the emailed code first, then use Google or X.";

    @Override
    protected void authenticateImpl(AuthenticationFlowContext context, SerializedBrokeredIdentityContext serializedCtx, BrokeredIdentityContext brokerContext) {
        UserModel existing = getExistingUser(context.getSession(), context.getRealm(), context.getAuthenticationSession());
        UserModel signedIn = context.getAuthenticationSession().getAuthenticatedUser();
        String signedInId = signedIn == null ? null : signedIn.getId();
        if (!DsBrokerLink.linkVerifiedEmail(signedInId, existing.isEmailVerified())) {
            sendFailureChallenge(context, Response.Status.CONFLICT, Errors.FEDERATED_IDENTITY_EXISTS,
                    UNVERIFIED, AuthenticationFlowError.IDENTITY_PROVIDER_ERROR);
            return;
        }
        context.setUser(existing);
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
