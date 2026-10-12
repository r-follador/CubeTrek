package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GarminOAuth2TokenServiceTest {
    private final UserThirdpartyConnectRepository repository = mock(UserThirdpartyConnectRepository.class);
    private final GarminApiClient api = mock(GarminApiClient.class);
    private final GarminOAuth2TokenService service = new GarminOAuth2TokenService(repository, api);
    private final UserThirdpartyConnect connection = new UserThirdpartyConnect();

    @BeforeEach
    void setup() {
        connection.setId(7L);
        connection.setGarminOauth2AccessToken("expired-access");
        connection.setGarminOauth2RefreshToken("refresh");
        connection.setGarminOauth2AccessTokenExpiresAt(Instant.now().minusSeconds(1));
        connection.setGarminOauth2RefreshTokenExpiresAt(Instant.now().plusSeconds(86_400));
        when(repository.lockById(7L)).thenReturn(Optional.of(connection));
    }

    @Test
    void rotationIsSavedAndNextReaderUsesFreshLockedState() throws Exception {
        when(api.refresh("refresh")).thenReturn(new GarminTokens("new-access", "rotated-refresh",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(90 * 86_400L)));
        assertEquals("new-access", service.getValidAccessToken(7L));
        assertEquals("new-access", service.getValidAccessToken(7L));
        assertEquals("rotated-refresh", connection.getGarminOauth2RefreshToken());
        verify(api, times(1)).refresh("refresh");
        verify(repository, times(2)).lockById(7L);
        verify(repository).saveAndFlush(connection);
    }

    @Test
    void invalidGrantMarksReconnectWithoutDeletingCredentials() throws Exception {
        when(api.refresh("refresh")).thenThrow(new GarminApiClient.ApiException(400, true));
        assertThrows(IOException.class, () -> service.getValidAccessToken(7L));
        assertTrue(connection.isGarminOauth2ReconnectRequired());
        assertEquals("refresh", connection.getGarminOauth2RefreshToken());
        verify(repository).saveAndFlush(connection);
        assertThrows(IOException.class, () -> service.getValidAccessToken(7L));
        verify(api, times(1)).refresh(anyString());
    }

    @Test
    void transientFailurePreservesTokensAndDoesNotRequireReconnect() throws Exception {
        when(api.refresh("refresh")).thenThrow(new GarminApiClient.ApiException(503, false));
        assertThrows(IOException.class, () -> service.getValidAccessToken(7L));
        assertFalse(connection.isGarminOauth2ReconnectRequired());
        assertEquals("refresh", connection.getGarminOauth2RefreshToken());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void expiredRefreshIsNotSentToGarmin() {
        connection.setGarminOauth2RefreshTokenExpiresAt(Instant.now().minusSeconds(1));
        assertThrows(IOException.class, () -> service.getValidAccessToken(7L));
        assertTrue(connection.isGarminOauth2ReconnectRequired());
        verifyNoInteractions(api);
        verify(repository).saveAndFlush(connection);
    }

    @Test
    void keepAliveRefreshesNearExpiryEvenWithValidAccessAndPermissionDisabled() throws Exception {
        connection.setGarminEnabled(false);
        connection.setGarminOauth2AccessTokenExpiresAt(Instant.now().plusSeconds(3600));
        when(api.refresh("refresh")).thenReturn(new GarminTokens("access", "rotated",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(90 * 86_400L)));
        service.keepAlive(7L);
        assertEquals("rotated", connection.getGarminOauth2RefreshToken());
        assertFalse(connection.isGarminEnabled());
    }

    @Test
    void explicitRefreshRotatesValidTokensUnderTheSameLock() throws Exception {
        connection.setGarminOauth2AccessTokenExpiresAt(Instant.now().plusSeconds(86_400));
        connection.setGarminOauth2RefreshTokenExpiresAt(Instant.now().plusSeconds(90 * 86_400L));
        connection.setGarminUseraccesstoken("legacy-token");
        when(api.refresh("refresh")).thenReturn(new GarminTokens("new-access", "rotated-refresh",
                Instant.now().plusSeconds(86_400), Instant.now().plusSeconds(90 * 86_400L)));

        assertEquals("new-access", service.refreshNow(7L));
        assertEquals("rotated-refresh", connection.getGarminOauth2RefreshToken());
        assertEquals("legacy-token", connection.getGarminUseraccesstoken());
        var order = inOrder(repository, api);
        order.verify(repository).lockById(7L);
        order.verify(api).refresh("refresh");
        order.verify(repository).saveAndFlush(connection);
    }

    @Test
    void explicitRefreshDoesNotBypassReconnectRequired() {
        connection.setGarminOauth2ReconnectRequired(true);
        assertThrows(IOException.class, () -> service.refreshNow(7L));
        verifyNoInteractions(api);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void maintenanceContinuesAfterAnIndividualFailure() throws Exception {
        var tokenService = mock(GarminOAuth2TokenService.class);
        when(repository.findGarminRefreshCandidates(any())).thenReturn(List.of(7L, 8L));
        doThrow(new IOException("transient")).when(tokenService).keepAlive(7L);
        new GarminTokenMaintenance(repository, tokenService).refreshExpiringConnections();
        verify(tokenService).keepAlive(7L);
        verify(tokenService).keepAlive(8L);
    }
}
