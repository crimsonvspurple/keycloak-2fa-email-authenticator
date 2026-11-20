package com.mesutpiskin.keycloak.auth.email.ds;

import java.util.stream.Stream;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import org.junit.jupiter.api.Test;
import org.keycloak.WebAuthnConstants;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.credential.CredentialModel;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DsPasskeyRegisterTest {

    private static final String CHROME_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Test
    void skipStillFinishesWithoutRegistering() {
        MultivaluedHashMap<String, String> form = new MultivaluedHashMap<>();
        form.add("skip", "on");
        RequiredActionContext context = mock(RequiredActionContext.class);
        HttpRequest request = mock(HttpRequest.class);
        when(context.getHttpRequest()).thenReturn(request);
        when(request.getDecodedFormParameters()).thenReturn(form);

        new DsPasskeyRegister(mock(KeycloakSession.class), null).processAction(context);

        verify(context).success();
    }

    @Test
    void rewritesADuplicateNameBeforeTheCredentialIsStored() {
        MultivaluedHashMap<String, String> form = new MultivaluedHashMap<>();
        form.add(WebAuthnConstants.AUTHENTICATOR_LABEL, "Chrome on Windows 4821");
        RequiredActionContext context = context(form, CHROME_WINDOWS, "Chrome on Windows 4821");

        DsPasskeyRegister.applyUniqueLabel(context, form);

        String label = form.getFirst(WebAuthnConstants.AUTHENTICATOR_LABEL);
        assertTrue(label.startsWith("Chrome on Windows "));
        assertFalse(label.equalsIgnoreCase("Chrome on Windows 4821"));
        assertTrue(label.length() <= PasskeyLabels.MAX_LENGTH);
    }

    @Test
    void namesABlankRegistrationFromTheBrowser() {
        MultivaluedHashMap<String, String> form = new MultivaluedHashMap<>();
        form.add(WebAuthnConstants.AUTHENTICATOR_LABEL, "Passkey (Default Label)");
        RequiredActionContext context = context(form, CHROME_WINDOWS);

        DsPasskeyRegister.applyUniqueLabel(context, form);

        String label = form.getFirst(WebAuthnConstants.AUTHENTICATOR_LABEL);
        assertTrue(label.matches("Chrome on Windows \\d{4}"));
    }

    @Test
    void leavesAnErrorSubmissionAlone() {
        MultivaluedHashMap<String, String> form = new MultivaluedHashMap<>();
        form.add(WebAuthnConstants.ERROR, "NotAllowedError: The operation either timed out or was not allowed.");
        form.add(WebAuthnConstants.AUTHENTICATOR_LABEL, "Chrome on Windows 4821");
        RequiredActionContext context = context(form, CHROME_WINDOWS, "Chrome on Windows 4821");

        DsPasskeyRegister.applyUniqueLabel(context, form);

        assertEquals("Chrome on Windows 4821", form.getFirst(WebAuthnConstants.AUTHENTICATOR_LABEL));
    }

    @Test
    void stillChoosesANameWhenExistingNamesCannotBeRead() {
        MultivaluedHashMap<String, String> form = new MultivaluedHashMap<>();
        form.add(WebAuthnConstants.AUTHENTICATOR_LABEL, "");
        RequiredActionContext context = mock(RequiredActionContext.class);
        HttpRequest request = mock(HttpRequest.class);
        HttpHeaders headers = mock(HttpHeaders.class);
        UserModel user = mock(UserModel.class);
        when(context.getHttpRequest()).thenReturn(request);
        when(request.getDecodedFormParameters()).thenReturn(form);
        when(request.getHttpHeaders()).thenReturn(headers);
        when(headers.getHeaderString(HttpHeaders.USER_AGENT)).thenReturn(CHROME_WINDOWS);
        when(context.getUser()).thenReturn(user);
        when(user.credentialManager()).thenThrow(new IllegalStateException("credential store unavailable"));

        DsPasskeyRegister.applyUniqueLabel(context, form);

        assertTrue(form.getFirst(WebAuthnConstants.AUTHENTICATOR_LABEL).matches("Chrome on Windows \\d{4}"));
    }

    @Test
    void ignoresAFormThatCannotBeUpdated() {
        @SuppressWarnings("unchecked")
        MultivaluedMap<String, String> form = mock(MultivaluedMap.class);
        when(form.getFirst(WebAuthnConstants.AUTHENTICATOR_LABEL)).thenReturn("Chrome on Windows 4821");
        doThrow(new UnsupportedOperationException("frozen"))
                .when(form).putSingle(eq(WebAuthnConstants.AUTHENTICATOR_LABEL), any());
        RequiredActionContext context = context(form, CHROME_WINDOWS, "Chrome on Windows 4821");

        assertDoesNotThrow(() -> DsPasskeyRegister.applyUniqueLabel(context, form));
        verify(form).putSingle(eq(WebAuthnConstants.AUTHENTICATOR_LABEL), any());
    }

    private static RequiredActionContext context(MultivaluedMap<String, String> form, String userAgent, String... labels) {
        RequiredActionContext context = mock(RequiredActionContext.class);
        HttpRequest request = mock(HttpRequest.class);
        HttpHeaders headers = mock(HttpHeaders.class);
        UserModel user = mock(UserModel.class);
        SubjectCredentialManager manager = mock(SubjectCredentialManager.class);
        when(context.getHttpRequest()).thenReturn(request);
        when(request.getDecodedFormParameters()).thenReturn(form);
        when(request.getHttpHeaders()).thenReturn(headers);
        when(headers.getHeaderString(HttpHeaders.USER_AGENT)).thenReturn(userAgent);
        when(context.getUser()).thenReturn(user);
        when(user.credentialManager()).thenReturn(manager);
        when(manager.getStoredCredentialsStream()).thenAnswer(invocation -> Stream.of(labels).map(label -> {
            CredentialModel credential = new CredentialModel();
            credential.setUserLabel(label);
            return credential;
        }));
        return context;
    }
}
