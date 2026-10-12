package com.mesutpiskin.keycloak.auth.email.ds;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.resetcred.ResetCredentialChooseUser;
import org.keycloak.events.EventBuilder;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Password-reset email step. Same user lookup as Keycloak's choose-user step, plus Turnstile.
 * A missing or disabled account still continues, so the page does not reveal which emails exist.
 */
public class DsResetEmailAuthenticator implements Authenticator {

    private static final String ACTION_TOKEN_USER = "ACTION_TOKEN_USER";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        if (session.getAuthNote(AbstractIdpAuthenticator.EXISTING_USER_INFO) != null) {
            UserModel existing = AbstractIdpAuthenticator.getExistingUser(context.getSession(), context.getRealm(), session);
            context.setUser(existing);
            challenge(context, null);
            return;
        }
        String actionTokenUser = session.getAuthNote(ACTION_TOKEN_USER);
        if (actionTokenUser != null) {
            UserModel user = context.getSession().users().getUserById(context.getRealm(), actionTokenUser);
            context.setUser(user);
            context.success();
            return;
        }
        AuthenticationManager.AuthResult cookie = AuthenticationManager.authenticateIdentityCookie(
                context.getSession(), context.getRealm(), true);
        if (cookie != null) {
            session.setAuthNote("ATTEMPTED_USERNAME", cookie.user().getUsername());
            context.setUser(cookie.user());
            context.success();
            return;
        }
        challenge(context, null);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        if (!Turnstile.allows(form.getFirst("cf-turnstile-response"), context.getConnection().getRemoteAddr(), Turnstile.RESET)) {
            challenge(context, Turnstile.REJECTED);
            return;
        }
        EventBuilder event = context.getEvent();
        String username = form.getFirst("username");
        if (username == null || username.isBlank()) {
            event.error("username_missing");
            challenge(context, "Enter the email on the account.");
            return;
        }
        username = username.trim();
        RealmModel realm = context.getRealm();
        UserModel user = context.getSession().users().getUserByUsername(realm, username);
        if (user == null && realm.isLoginWithEmailAllowed() && username.contains("@")) {
            user = context.getSession().users().getUserByEmail(realm, username);
        }
        context.getAuthenticationSession().setAuthNote("ATTEMPTED_USERNAME", username);
        if (user == null) {
            event.clone().detail("username", username).error("user_not_found");
            context.clearUser();
        } else if (!user.isEnabled()) {
            event.clone().detail("username", username).user(user).error("user_disabled");
            context.clearUser();
        } else {
            context.getAuthenticationSession().setAuthNote(ResetCredentialChooseUser.RESET_CREDENTIAL_USER_CHOSEN, "true");
            context.setUser(user);
        }
        context.success();
    }

    private void challenge(AuthenticationFlowContext context, String error) {
        LoginFormsProvider form = context.form().setAttribute("dsTurnstileSiteKey", Turnstile.siteKey());
        if (error != null) {
            form.setError(error);
        }
        Response page = form.createPasswordReset();
        context.challenge(page);
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}
