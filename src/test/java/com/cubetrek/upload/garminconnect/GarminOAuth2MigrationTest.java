package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.cubetrek.upload.garminconnect.GarminOAuth2MigrationService.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GarminOAuth2MigrationTest {
    private final UserThirdpartyConnectRepository repository = mock(UserThirdpartyConnectRepository.class);
    private final GarminApiClient api = mock(GarminApiClient.class);
    private final GarminOAuth2TokenService tokens = mock(GarminOAuth2TokenService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final GarminOAuth2MigrationService migration = new GarminOAuth2MigrationService(repository, api, tokens, transactions);
    private final UserThirdpartyConnect connection = new UserThirdpartyConnect();

    @BeforeEach
    void setup() throws Exception {
        connection.setId(7L);
        connection.setGarminUserid("backfilled-id");
        connection.setGarminUseraccesstoken("oauth1");
        connection.setGarminUseraccesstokenSecret("secret");
        when(repository.lockById(7L)).thenReturn(Optional.of(connection));
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(api.exchangeOAuth1("oauth1", "secret")).thenReturn(new GarminTokens("access", "refresh",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(90 * 86_400L)));
        when(tokens.getValidAccessToken(7L)).thenReturn("access");
        when(api.userId("access")).thenReturn("backfilled-id");
    }

    @Test
    void backfilledDisabledConnectionIsMigratedWithoutLosingOauth1OverlapCredentials() throws Exception {
        connection.setGarminEnabled(false);
        when(api.activityExportAllowed("access")).thenReturn(false);
        assertEquals(MIGRATED, migration.migrate(7L).status());
        assertEquals("backfilled-id", connection.getGarminUserid());
        assertEquals("refresh", connection.getGarminOauth2RefreshToken());
        assertEquals("oauth1", connection.getGarminUseraccesstoken());
        assertNotNull(connection.getGarminOauth2MigrationCompletedAt());
        assertFalse(connection.isGarminEnabled());
        assertEquals(SKIPPED, migration.migrate(7L).status());
        verify(api, times(1)).exchangeOAuth1("oauth1", "secret");
    }

    @Test
    void commitsIssuedTokensBeforeIdentityLookupAndResumesWithoutReexchange() throws Exception {
        when(api.userId("access")).thenThrow(new IOException("temporary failure")).thenReturn("backfilled-id");
        assertEquals(FAILED, migration.migrate(7L).status());
        assertEquals("refresh", connection.getGarminOauth2RefreshToken());
        assertNull(connection.getGarminOauth2MigrationCompletedAt());
        var order = inOrder(api, repository, transactions);
        order.verify(repository).saveAndFlush(connection);
        order.verify(transactions).commit(any());
        order.verify(api).exchangeOAuth1("oauth1", "secret");
        order.verify(repository).saveAndFlush(connection);
        order.verify(transactions).commit(any());
        order.verify(api).userId("access");

        assertEquals(MIGRATED, migration.migrate(7L).status());
        verify(api, times(1)).exchangeOAuth1("oauth1", "secret");
    }

    @Test
    void unknownExchangeOutcomeIsNotReplayedAutomatically() throws Exception {
        when(api.exchangeOAuth1("oauth1", "secret")).thenThrow(new IOException("timeout"));
        assertEquals(FAILED, migration.migrate(7L).status());
        assertNotNull(connection.getGarminOauth2ExchangeStartedAt());
        assertNull(connection.getGarminOauth2RefreshToken());
        assertEquals(FAILED, migration.migrate(7L).status());
        verify(api, times(1)).exchangeOAuth1("oauth1", "secret");
        verifyNoInteractions(tokens);
    }

    @Test
    void permissionFailurePreservesTokensAndEnabledStateAndResumesWithoutReexchange() throws Exception {
        connection.setGarminEnabled(true);
        when(api.activityExportAllowed("access")).thenThrow(new IOException("unexpected permissions format"))
                .thenReturn(true);

        assertEquals(FAILED, migration.migrate(7L).status());
        Instant startedAt = connection.getGarminOauth2ExchangeStartedAt();
        assertNotNull(startedAt);
        assertEquals("refresh", connection.getGarminOauth2RefreshToken());
        assertTrue(connection.isGarminEnabled());
        assertNull(connection.getGarminOauth2MigrationCompletedAt());

        assertEquals(MIGRATED, migration.migrate(7L).status());
        assertEquals(startedAt, connection.getGarminOauth2ExchangeStartedAt());
        assertTrue(connection.isGarminEnabled());
        assertNotNull(connection.getGarminOauth2MigrationCompletedAt());
        verify(api, times(1)).exchangeOAuth1("oauth1", "secret");
    }

    @Test
    void backfillIsRequiredBeforeFirstExchange() {
        connection.setGarminUserid(null);
        assertEquals(FAILED, migration.migrate(7L).status());
        assertNull(connection.getGarminOauth2ExchangeStartedAt());
        verifyNoInteractions(api, tokens);
    }

    @Test
    void identityMismatchDoesNotMarkCompleteOrOverwriteBackfilledId() throws Exception {
        when(api.userId("access")).thenReturn("unexpected-id");
        assertEquals(FAILED, migration.migrate(7L).status());
        assertEquals("backfilled-id", connection.getGarminUserid());
        assertNull(connection.getGarminOauth2MigrationCompletedAt());
        assertEquals("refresh", connection.getGarminOauth2RefreshToken());
    }

    @Test
    void relinkDuringExchangeCannotBeOverwritten() throws Exception {
        when(api.exchangeOAuth1("oauth1", "secret")).thenAnswer(invocation -> {
            connection.setGarminUseraccesstoken("new-link");
            return new GarminTokens("access", "refresh", Instant.now().plusSeconds(3600), null);
        });
        assertEquals(FAILED, migration.migrate(7L).status());
        assertEquals("new-link", connection.getGarminUseraccesstoken());
        assertNull(connection.getGarminOauth2AccessToken());
        verifyNoInteractions(tokens);
    }

    @Test
    void commandDryRunDoesNotContactGarminOrMigrate() throws Exception {
        var service = mock(GarminOAuth2MigrationService.class);
        when(repository.findGarminMigrationCandidates()).thenReturn(List.of(7L, 8L));
        assertEquals(0, new GarminOAuth2MigrationCommand(repository, service, api).run(true, 1, null));
        verifyNoInteractions(service, api);
    }

    @Test
    void commandSupportsSingleConnectionPilotAndReportsFailures() throws Exception {
        var service = mock(GarminOAuth2MigrationService.class);
        when(repository.findGarminMigrationCandidates()).thenReturn(List.of(7L, 8L));
        when(service.migrate(8L)).thenReturn(new GarminOAuth2MigrationService.MigrationResult(FAILED, "failure"));
        assertEquals(1, new GarminOAuth2MigrationCommand(repository, service, api).run(false, 1, 8L));
        verify(service).migrate(8L);
        verifyNoMoreInteractions(service);
    }
}
