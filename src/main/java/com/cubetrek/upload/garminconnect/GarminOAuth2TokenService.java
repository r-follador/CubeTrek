package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Service
public class GarminOAuth2TokenService {
    static final Duration REFRESH_MARGIN = Duration.ofMinutes(10);
    static final Duration KEEP_ALIVE_MARGIN = Duration.ofDays(7);
    private final UserThirdpartyConnectRepository repository;
    private final GarminApiClient api;

    public GarminOAuth2TokenService(UserThirdpartyConnectRepository repository, GarminApiClient api) {
        this.repository = repository;
        this.api = api;
    }

    @Transactional
    public String getValidAccessToken(Long connectionId) throws IOException, InterruptedException {
        return accessToken(connectionId, RefreshMode.IF_NEEDED);
    }

    @Transactional
    public void keepAlive(Long connectionId) throws IOException, InterruptedException {
        accessToken(connectionId, RefreshMode.KEEP_ALIVE);
    }

    @Transactional
    public String refreshNow(Long connectionId) throws IOException, InterruptedException {
        return accessToken(connectionId, RefreshMode.FORCE);
    }

    private String accessToken(Long id, RefreshMode mode) throws IOException, InterruptedException {
        // Read fresh tokens under a database lock shared by download, keep-alive, and relinking paths.
        UserThirdpartyConnect connection = repository.lockById(id).orElseThrow(() -> new IOException("Garmin connection no longer exists"));
        Instant now = Instant.now();
        if (connection.isGarminOauth2ReconnectRequired()) {
            throw new IOException("Garmin reconnection is required");
        }
        boolean refreshExpiring = connection.getGarminOauth2RefreshTokenExpiresAt() == null
                || !connection.getGarminOauth2RefreshTokenExpiresAt().isAfter(now.plus(KEEP_ALIVE_MARGIN));
        boolean accessValid = !blank(connection.getGarminOauth2AccessToken())
                && connection.getGarminOauth2AccessTokenExpiresAt() != null
                && connection.getGarminOauth2AccessTokenExpiresAt().isAfter(now.plus(REFRESH_MARGIN));
        if (mode != RefreshMode.FORCE && accessValid && !(mode == RefreshMode.KEEP_ALIVE && refreshExpiring)) {
            return connection.getGarminOauth2AccessToken();
        }
        if (blank(connection.getGarminOauth2RefreshToken())) {
            throw new IOException("Garmin OAuth 2 refresh token is missing");
        }
        if (connection.getGarminOauth2RefreshTokenExpiresAt() != null
                && !connection.getGarminOauth2RefreshTokenExpiresAt().isAfter(now)) {
            connection.setGarminOauth2ReconnectRequired(true);
            repository.saveAndFlush(connection);
            throw new IOException("Garmin refresh token expired; reconnection is required");
        }
        try {
            api.refresh(connection.getGarminOauth2RefreshToken()).applyTo(connection);
            repository.saveAndFlush(connection);
            return connection.getGarminOauth2AccessToken();
        } catch (GarminApiClient.ApiException e) {
            if (e.invalidGrant()) {
                connection.setGarminOauth2ReconnectRequired(true);
                repository.saveAndFlush(connection);
            }
            // Checked failures commit the reconnect flag; transient failures leave stored tokens intact.
            throw e;
        }
    }

    static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private enum RefreshMode { IF_NEEDED, KEEP_ALIVE, FORCE }
}
