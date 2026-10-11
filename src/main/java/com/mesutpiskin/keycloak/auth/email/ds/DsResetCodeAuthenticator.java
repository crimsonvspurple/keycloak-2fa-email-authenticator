package com.mesutpiskin.keycloak.auth.email.ds;

import jakarta.ws.rs.core.Response;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Reset step after the email is known. Sends a code and a same-browser link, then the
 * built-in update-password page runs.
 */
public class DsResetCodeAuthenticator implements Authenticator {

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        if (DsAuthCodes.magicMatches(context)) {
            context.success();
            return;
        }
        if (context.getAuthenticationSession().getAuthNote(DsAuthCodes.CODE_HASH) == null) {
            send(context);
            return;
        }
        context.challenge(form(context, null));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        if (DsAuthCodes.pressed(context, "resend")) {
            send(context);
            return;
        }
        String error = DsAuthCodes.rejectCode(context, DsAuthCodes.formValue(context, "code"));
        if (error == null) {
            context.success();
            return;
        }
        context.challenge(form(context, error));
    }

    private void send(AuthenticationFlowContext context) {
        boolean sent = DsAuthCodes.send(
                context,
                "password-reset",
                "Your DobberSports password code",
                "Use this code to set a password on your DobberSports account. If you did not ask for it, you can ignore this message.");
        context.challenge(form(context, sent ? null : "The code could not be sent. Try again in a moment."));
    }

    private Response form(AuthenticationFlowContext context, String error) {
        UserModel user = context.getUser();
        LoginFormsProvider provider = context.form()
                .setAttribute("dsEmail", user == null ? "" : user.getEmail());
        if (error != null) {
            provider.setError(error);
        }
        return provider.createForm("ds-reset-code.ftl");
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return user.getEmail() != null && !user.getEmail().isBlank();
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}
