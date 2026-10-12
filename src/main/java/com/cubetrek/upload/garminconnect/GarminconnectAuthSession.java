package com.cubetrek.upload.garminconnect;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class GarminconnectAuthSession {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration LIFETIME = Duration.ofMinutes(10);
    private Pending pending;

    public synchronized void beginOAuth1(Long userId, OAuthConsumer consumer, OAuthProvider provider) {
        pending = new Pending(userId, Instant.now(), consumer, provider, null, null, null);
    }

    public synchronized Pending beginOAuth2(Long userId, String redirectUri) {
        pending = new Pending(userId, Instant.now(), null, null, random(), random(), redirectUri);
        return pending;
    }

    public synchronized Pending consumeOAuth1(Long userId, String token) {
        Pending result = consume(userId);
        if (result.consumer == null || token == null || !Objects.equals(result.consumer.getToken(), token)) {
            throw new IllegalArgumentException("Invalid Garmin OAuth 1 callback");
        }
        return result;
    }

    public synchronized Pending consumeOAuth2(Long userId, String state) {
        Pending result = consume(userId);
        if (result.state == null || state == null || !MessageDigest.isEqual(
                result.state.getBytes(StandardCharsets.US_ASCII), state.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Invalid Garmin OAuth 2 state");
        }
        return result;
    }

    private Pending consume(Long userId) {
        Pending result = pending;
        pending = null;
        if (result == null || !Objects.equals(result.userId, userId)
                || !result.createdAt.plus(LIFETIME).isAfter(Instant.now())) {
            throw new IllegalArgumentException("Garmin authorization session expired");
        }
        return result;
    }

    private static String random() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record Pending(Long userId, Instant createdAt, OAuthConsumer consumer, OAuthProvider provider,
                          String state, String verifier, String redirectUri) {
        public String challenge() {
            try {
                return Base64.getUrlEncoder().withoutPadding().encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String toString() { return "GarminAuthorization[redacted]"; }
    }
}
