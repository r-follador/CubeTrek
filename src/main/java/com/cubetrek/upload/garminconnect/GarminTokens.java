package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.UserThirdpartyConnect;

import java.time.Instant;

public record GarminTokens(String accessToken, String refreshToken, Instant accessExpiresAt, Instant refreshExpiresAt) {
    public void applyTo(UserThirdpartyConnect connection) {
        connection.setGarminOauth2AccessToken(accessToken);
        connection.setGarminOauth2RefreshToken(refreshToken);
        connection.setGarminOauth2AccessTokenExpiresAt(accessExpiresAt);
        connection.setGarminOauth2RefreshTokenExpiresAt(refreshExpiresAt);
        connection.setGarminOauth2ReconnectRequired(false);
    }

    public static void clear(UserThirdpartyConnect connection) {
        connection.setGarminOauth2AccessToken(null);
        connection.setGarminOauth2RefreshToken(null);
        connection.setGarminOauth2AccessTokenExpiresAt(null);
        connection.setGarminOauth2RefreshTokenExpiresAt(null);
        connection.setGarminOauth2ExchangeStartedAt(null);
        connection.setGarminOauth2MigrationCompletedAt(null);
        connection.setGarminOauth2ReconnectRequired(false);
    }

    @Override
    public String toString() {
        return "GarminTokens[redacted]";
    }
}
