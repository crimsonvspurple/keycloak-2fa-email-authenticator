package com.mesutpiskin.keycloak.auth.email.ds;

import java.util.List;

import jakarta.ws.rs.core.MultivaluedMap;

import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;

/** Checks Turnstile on the Keycloak registration form. */
public class DsTurnstileRegistration implements FormAction {

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {
        if (Turnstile.configured()) {
            form.setAttribute("dsTurnstileSiteKey", Turnstile.siteKey());
        }
    }

    @Override
    public void validate(ValidationContext context) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        if (!Turnstile.configured()) {
            context.success();
            return;
        }
        if (Turnstile.allows(form.getFirst("cf-turnstile-response"), context.getConnection().getRemoteAddr(), Turnstile.REGISTER)) {
            context.success();
            return;
        }
        context.error(Errors.INVALID_REGISTRATION);
        context.validationError(form, List.of(new FormMessage(null, Turnstile.REJECTED)));
    }

    @Override
    public void success(FormContext context) {
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
