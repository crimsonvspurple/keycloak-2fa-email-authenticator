package com.mesutpiskin.keycloak.auth.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link EmailAuthenticatorCredentialProvider#isConfiguredFor}
 * depends solely on stored credentials and does not touch realm flow state.
 * The previous flow-scanning behaviour (issue #129) is intentionally absent.
 */
class EmailAuthenticatorCredentialProviderTest {

    private EmailAuthenticatorCredentialProvider provider;
    private RealmModel realm;
    private UserModel user;
    private SubjectCredentialManager credentialManager;

    @BeforeEach
    void setUp() {
        KeycloakSession session = mock(KeycloakSession.class);
        realm = mock(RealmModel.class);
        user = mock(UserModel.class);
        credentialManager = mock(SubjectCredentialManager.class);
        when(user.credentialManager()).thenReturn(credentialManager);
        provider = new EmailAuthenticatorCredentialProvider(session);
    }

    @Test
    @DisplayName("Returns true only when a stored credential exists")
    void testIsConfiguredFor_WithStoredCredential() {
        var credential = new EmailAuthenticatorCredentialModel();
        when(credentialManager.getStoredCredentialsByTypeStream(EmailAuthenticatorCredentialModel.TYPE_ID))
                .thenReturn(Stream.of(credential));

        boolean result = provider.isConfiguredFor(realm, user, EmailAuthenticatorCredentialModel.TYPE_ID);

        assertTrue(result, "Should be configured when a stored credential exists");
    }

    @Test
    @DisplayName("Returns false when no stored credential exists, regardless of user email")
    void testIsConfiguredFor_NoStoredCredential() {
        when(credentialManager.getStoredCredentialsByTypeStream(EmailAuthenticatorCredentialModel.TYPE_ID))
                .thenReturn(Stream.empty());

        boolean result = provider.isConfiguredFor(realm, user, EmailAuthenticatorCredentialModel.TYPE_ID);

        assertFalse(result, "Provider should report false when no credential is stored");
    }

    @Test
    @DisplayName("Returns false for unsupported credential types")
    void testIsConfiguredFor_WrongCredentialType() {
        boolean result = provider.isConfiguredFor(realm, user, "wrong-type");

        assertFalse(result, "Should return false for unsupported credential type");
    }

    @Test
    @DisplayName("Does not scan realm flows (decoupled from flow execution config)")
    void testIsConfiguredFor_DoesNotTouchRealmFlows() {
        when(credentialManager.getStoredCredentialsByTypeStream(EmailAuthenticatorCredentialModel.TYPE_ID))
                .thenReturn(Stream.empty());

        provider.isConfiguredFor(realm, user, EmailAuthenticatorCredentialModel.TYPE_ID);

        verifyNoInteractions(realm);
    }
}
