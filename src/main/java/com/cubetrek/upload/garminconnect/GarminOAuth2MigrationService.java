package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;

import static com.cubetrek.upload.garminconnect.GarminOAuth2TokenService.blank;

@Service
public class GarminOAuth2MigrationService {
    private static final Logger logger = LoggerFactory.getLogger(GarminOAuth2MigrationService.class);
    private final UserThirdpartyConnectRepository repository;
    private final GarminApiClient api;
    private final GarminOAuth2TokenService tokens;
    private final TransactionTemplate transaction;

    public GarminOAuth2MigrationService(UserThirdpartyConnectRepository repository, GarminApiClient api,
                                       GarminOAuth2TokenService tokens, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.api = api;
        this.tokens = tokens;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public MigrationResult migrate(Long id) {
        try {
            Snapshot snapshot = transaction.execute(status -> prepare(id));
            if (snapshot == null) return new MigrationResult(Status.SKIPPED, "Already migrated or no OAuth 1 connection");
            if (snapshot.exchangeNeeded) {
                GarminTokens issued = api.exchangeOAuth1(snapshot.token, snapshot.secret);
                // Commit issued credentials before making any further Garmin requests.
                transaction.executeWithoutResult(status -> {
                    UserThirdpartyConnect connection = unchanged(id, snapshot);
                    issued.applyTo(connection);
                    repository.saveAndFlush(connection);
                });
            }

            String access = tokens.getValidAccessToken(id);
            String userId = api.userId(access);
            boolean activityExportAllowed = api.activityExportAllowed(access);
            transaction.executeWithoutResult(status -> {
                UserThirdpartyConnect connection = unchanged(id, snapshot);
                if (!blank(connection.getGarminUserid()) && !connection.getGarminUserid().equals(userId)) {
                    throw new IllegalStateException("Garmin identity changed; review the connection");
                }
                UserThirdpartyConnect other = repository.findByGarminUserid(userId);
                if (other != null && !Objects.equals(id, other.getId())) {
                    throw new IllegalStateException("Garmin ID already belongs to another connection");
                }
                connection.setGarminUserid(userId);
                connection.setGarminEnabled(activityExportAllowed);
                connection.setGarminOauth2MigrationCompletedAt(Instant.now());
                repository.saveAndFlush(connection);
            });
            return new MigrationResult(Status.MIGRATED, "OAuth 2 credentials and identity verified");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new MigrationResult(Status.FAILED, "Interrupted; rerun to inspect saved state");
        } catch (Exception e) {
            logger.warn("Garmin migration failed: connection={}, errorType={}", id, e.getClass().getSimpleName());
            return new MigrationResult(Status.FAILED, "Inspect saved migration state before retrying");
        }
    }

    private Snapshot prepare(Long id) {
        UserThirdpartyConnect connection = repository.lockById(id).orElseThrow();
        if (connection.getGarminOauth2MigrationCompletedAt() != null || blank(connection.getGarminUseraccesstoken())) return null;
        if (connection.isGarminOauth2ReconnectRequired()) throw new IllegalStateException("Reconnect first");
        boolean exchangeNeeded = blank(connection.getGarminOauth2RefreshToken());
        if (exchangeNeeded) {
            if (connection.getGarminOauth2ExchangeStartedAt() != null) {
                throw new IllegalStateException("Previous exchange outcome unknown; automatic re-exchange refused");
            }
            if (blank(connection.getGarminUserid()) || blank(connection.getGarminUseraccesstokenSecret())) {
                throw new IllegalStateException("Backfill the Garmin ID and verify OAuth 1 credentials first");
            }
            connection.setGarminOauth2ExchangeStartedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            repository.saveAndFlush(connection);
        }
        return new Snapshot(connection.getGarminUseraccesstoken(), connection.getGarminUseraccesstokenSecret(),
                connection.getGarminOauth2ExchangeStartedAt(), exchangeNeeded);
    }

    private UserThirdpartyConnect unchanged(Long id, Snapshot snapshot) {
        UserThirdpartyConnect connection = repository.lockById(id).orElseThrow();
        if (!Objects.equals(connection.getGarminUseraccesstoken(), snapshot.token)
                || !Objects.equals(connection.getGarminOauth2ExchangeStartedAt(), snapshot.startedAt)) {
            throw new IllegalStateException("Garmin connection changed during migration");
        }
        return connection;
    }

    private record Snapshot(String token, String secret, Instant startedAt, boolean exchangeNeeded) {
        @Override
        public String toString() { return "GarminMigrationSnapshot[redacted]"; }
    }

    public record MigrationResult(Status status, String detail) { }
    public enum Status { MIGRATED, SKIPPED, FAILED }
}
