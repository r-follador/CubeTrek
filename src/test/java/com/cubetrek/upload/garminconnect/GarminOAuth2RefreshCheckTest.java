package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GarminOAuth2RefreshCheckTest {
    private final UserThirdpartyConnectRepository repository = mock(UserThirdpartyConnectRepository.class);
    private final GarminOAuth2TokenService tokens = mock(GarminOAuth2TokenService.class);
    private final GarminApiClient api = mock(GarminApiClient.class);
    private final GarminOAuth2RefreshCheck check = new GarminOAuth2RefreshCheck(repository, tokens, api);
    private final UserThirdpartyConnect connection = new UserThirdpartyConnect();

    @BeforeEach
    void setup() {
        connection.setId(7L);
        connection.setGarminUserid("stable-id");
        connection.setGarminEnabled(true);
        connection.setGarminOauth2MigrationCompletedAt(Instant.now());
        connection.setGarminOauth2AccessToken("old-access");
        connection.setGarminOauth2RefreshToken("old-refresh");
        connection.setGarminOauth2AccessTokenExpiresAt(Instant.now().plusSeconds(86_400));
        when(repository.findById(7L)).thenReturn(Optional.of(connection));
    }

    private void successfulRefresh() throws Exception {
        when(tokens.refreshNow(7L)).thenAnswer(invocation -> {
            connection.setGarminOauth2AccessToken("new-access");
            connection.setGarminOauth2RefreshToken("new-refresh");
            return "new-access";
        });
        when(api.userId("new-access")).thenReturn("stable-id");
        when(api.activityExportAllowed("new-access")).thenReturn(true);
    }

    @Test
    void requiresExplicitPositiveConnectionId() {
        assertEquals(1, check.run(false, null));
        assertEquals(1, check.run(false, 0L));
        verifyNoInteractions(repository, tokens, api);
    }

    @Test
    void dryRunChecksReadinessWithoutContactingGarminOrRotatingTokens() {
        assertEquals(0, check.run(true, 7L));
        verifyNoInteractions(tokens, api);
        verify(repository).findById(7L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void requiresCompletedMigrationAndUsableCredentials() {
        connection.setGarminOauth2MigrationCompletedAt(null);
        assertEquals(1, check.run(false, 7L));
        connection.setGarminOauth2MigrationCompletedAt(Instant.now());
        connection.setGarminOauth2ReconnectRequired(true);
        assertEquals(1, check.run(false, 7L));
        connection.setGarminOauth2ReconnectRequired(false);
        connection.setGarminOauth2RefreshToken("");
        assertEquals(1, check.run(false, 7L));
        verifyNoInteractions(tokens, api);
    }

    @Test
    void refreshesOneConnectionThenChecksPersistenceIdentityAndPermissions() throws Exception {
        successfulRefresh();
        assertEquals(0, check.run(false, 7L));
        var order = inOrder(repository, tokens, api);
        order.verify(repository).findById(7L);
        order.verify(tokens).refreshNow(7L);
        order.verify(repository).findById(7L);
        order.verify(api).userId("new-access");
        order.verify(api).activityExportAllowed("new-access");
        order.verifyNoMoreInteractions();
        assertTrue(connection.isGarminEnabled());
    }

    @Test
    void doesNotClaimSuccessWhenRotatedTokensWereNotSaved() throws Exception {
        when(tokens.refreshNow(7L)).thenReturn("new-access");
        assertEquals(1, check.run(false, 7L));
        verifyNoInteractions(api);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void identityMismatchFailsWithoutOverwritingSavedTokens() throws Exception {
        successfulRefresh();
        when(api.userId("new-access")).thenReturn("different-id");
        assertEquals(1, check.run(false, 7L));
        assertEquals("new-refresh", connection.getGarminOauth2RefreshToken());
        verify(api, never()).activityExportAllowed(anyString());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void refreshFailureIsNotRetriedAutomatically() throws Exception {
        when(tokens.refreshNow(7L)).thenThrow(new IOException("uncertain outcome"));
        assertEquals(1, check.run(false, 7L));
        verify(tokens).refreshNow(7L);
        verifyNoMoreInteractions(tokens);
        verifyNoInteractions(api);
    }

    @Test
    void permissionFailureKeepsTheRotatedCredentials() throws Exception {
        successfulRefresh();
        when(api.activityExportAllowed("new-access")).thenThrow(new IOException("temporary failure"));
        assertEquals(1, check.run(false, 7L));
        assertEquals("new-refresh", connection.getGarminOauth2RefreshToken());
        assertTrue(connection.isGarminEnabled());
        verify(tokens).refreshNow(7L);
    }

    @Test
    void permissionOptOutDoesNotTurnIngestionOn() throws Exception {
        successfulRefresh();
        connection.setGarminEnabled(false);
        when(api.activityExportAllowed("new-access")).thenReturn(false);
        assertEquals(0, check.run(false, 7L));
        assertFalse(connection.isGarminEnabled());
    }
}
