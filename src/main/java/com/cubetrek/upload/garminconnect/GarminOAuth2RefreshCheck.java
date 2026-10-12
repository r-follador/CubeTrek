package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.UserThirdpartyConnectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

import static com.cubetrek.upload.garminconnect.GarminOAuth2TokenService.blank;

// Imported only by the standalone CLI; never runs from application startup or a web endpoint.
public class GarminOAuth2RefreshCheck {
    private static final Logger logger = LoggerFactory.getLogger(GarminOAuth2RefreshCheck.class);
    private final UserThirdpartyConnectRepository repository;
    private final GarminOAuth2TokenService tokens;
    private final GarminApiClient api;

    public GarminOAuth2RefreshCheck(UserThirdpartyConnectRepository repository,
                                   GarminOAuth2TokenService tokens, GarminApiClient api) {
        this.repository = repository;
        this.tokens = tokens;
        this.api = api;
    }

    public int run(boolean dryRun, Long connectionId) {
        String stage = "preflight";
        try {
            if (connectionId == null || connectionId <= 0) {
                throw new IllegalArgumentException("An explicit connection ID is required");
            }
            var before = repository.findById(connectionId).orElseThrow();
            if (before.getGarminOauth2MigrationCompletedAt() == null || blank(before.getGarminUserid())
                    || blank(before.getGarminOauth2RefreshToken()) || before.isGarminOauth2ReconnectRequired()) {
                throw new IllegalStateException("A completed migration with usable OAuth 2 credentials is required");
            }
            String expectedUserId = before.getGarminUserid();
            String previousRefresh = before.getGarminOauth2RefreshToken();
            logger.info("Garmin refresh check: connection={}, dryRun={}", connectionId, dryRun);
            if (dryRun) return 0;

            stage = "refresh";
            // The proxied service commits the rotated pair before any verification requests.
            String access = tokens.refreshNow(connectionId);
            stage = "persistence";
            var saved = repository.findById(connectionId).orElseThrow();
            if (!Objects.equals(access, saved.getGarminOauth2AccessToken())
                    || blank(saved.getGarminOauth2RefreshToken())
                    || Objects.equals(previousRefresh, saved.getGarminOauth2RefreshToken())
                    || !Objects.equals(expectedUserId, saved.getGarminUserid())
                    || saved.isGarminOauth2ReconnectRequired()
                    || saved.getGarminOauth2AccessTokenExpiresAt() == null
                    || !saved.getGarminOauth2AccessTokenExpiresAt().isAfter(Instant.now())) {
                throw new IllegalStateException("Saved credentials did not match the refreshed pair");
            }
            stage = "identity";
            if (!expectedUserId.equals(api.userId(access))) {
                throw new IllegalStateException("Garmin identity changed");
            }
            stage = "permissions";
            boolean activityExportAllowed = api.activityExportAllowed(access);
            logger.info("Garmin refresh check successful: connection={}, rotatedTokensSaved=true, identityVerified=true, activityExportAllowed={}",
                    connectionId, activityExportAllowed);
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Garmin refresh check interrupted: connection={}, stage={}; inspect saved state before retrying", connectionId, stage);
        } catch (Exception e) {
            logger.warn("Garmin refresh check failed: connection={}, stage={}, errorType={}, httpStatus={}; inspect saved state before retrying",
                    connectionId, stage, e.getClass().getSimpleName(),
                    e instanceof GarminApiClient.ApiException failure ? failure.status() : null);
        }
        return 1;
    }
}
