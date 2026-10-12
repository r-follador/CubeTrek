package com.cubetrek.upload.garminconnect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.exception.OAuthException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GarminApiClient {
    private static final String USER_URL = "https://apis.garmin.com/partner-gateway/rest/user/";
    private static final String TOKEN_URL = "https://diauth.garmin.com/di-oauth2-service/oauth/token";
    private final String consumerKey;
    private final String consumerSecret;
    private final String clientId;
    private final String clientSecret;
    private final ObjectMapper mapper;
    private final HttpClient http;

    @Autowired
    public GarminApiClient(@Value("${garmin.consumer.key}") String consumerKey,
                           @Value("${garmin.consumer.secret}") String consumerSecret,
                           @Value("${garmin.oauth2.client-id:}") String clientId,
                           @Value("${garmin.oauth2.client-secret:}") String clientSecret,
                           ObjectMapper mapper) {
        this(consumerKey, consumerSecret, clientId, clientSecret, mapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    GarminApiClient(String consumerKey, String consumerSecret, String clientId, String clientSecret,
                    ObjectMapper mapper, HttpClient http) {
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.mapper = mapper;
        this.http = http;
    }

    public String oauth2ClientId() throws IOException {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IOException("Garmin OAuth 2 client credentials are not configured");
        }
        return clientId;
    }

    public String userIdOAuth1(String token, String secret) throws IOException {
        return requiredText(oauth1("GET", "id", token, secret), "userId");
    }

    public GarminTokens exchangeOAuth1(String token, String secret) throws IOException {
        Instant requestedAt = Instant.now();
        return tokens(oauth1("POST", "token-exchange", token, secret), requestedAt);
    }

    public GarminTokens exchangeCode(String code, String verifier, String redirectUri) throws IOException, InterruptedException {
        return tokenRequest(Map.of("grant_type", "authorization_code", "code", code,
                "code_verifier", verifier, "redirect_uri", redirectUri));
    }

    public GarminTokens refresh(String refreshToken) throws IOException, InterruptedException {
        return tokenRequest(Map.of("grant_type", "refresh_token", "refresh_token", refreshToken));
    }

    public String userId(String accessToken) throws IOException, InterruptedException {
        return requiredText(bearerGet("id", accessToken), "userId");
    }

    public boolean activityExportAllowed(String accessToken) throws IOException, InterruptedException {
        JsonNode response = bearerGet("permissions", accessToken);
        // Garmin's live response wraps the array in "permissions"; also accept the bare-array form.
        JsonNode permissions = response.isObject() ? response.path("permissions") : response;
        if (!permissions.isArray()) {
            throw new IOException("Garmin permissions response omitted a permissions array");
        }
        boolean allowed = false;
        for (JsonNode permission : permissions) {
            if (!permission.isTextual()) {
                throw new IOException("Garmin permissions response contains a non-string value");
            }
            allowed |= "ACTIVITY_EXPORT".equals(permission.textValue());
        }
        return allowed;
    }

    private JsonNode bearerGet(String operation, String accessToken) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(USER_URL + operation))
                .header("Authorization", "Bearer " + accessToken).GET());
    }

    private GarminTokens tokenRequest(Map<String, String> fields) throws IOException, InterruptedException {
        oauth2ClientId();
        String form = fields.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        Instant requestedAt = Instant.now();
        JsonNode response = send(HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)));
        return tokens(response, requestedAt);
    }

    private JsonNode send(HttpRequest.Builder request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request.timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
        return response(response.statusCode(), response.body());
    }

    private JsonNode oauth1(String method, String operation, String token, String secret) throws IOException {
        HttpURLConnection request = (HttpURLConnection) URI.create(USER_URL + operation).toURL().openConnection();
        try {
            request.setRequestMethod(method);
            request.setInstanceFollowRedirects(false);
            request.setConnectTimeout(30_000);
            request.setReadTimeout(30_000);
            DefaultOAuthConsumer consumer = new DefaultOAuthConsumer(consumerKey, consumerSecret);
            consumer.setTokenWithSecret(token, secret);
            consumer.sign(request);
            int status = request.getResponseCode();
            try (InputStream stream = status >= 400 ? request.getErrorStream() : request.getInputStream()) {
                return response(status, stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (OAuthException e) {
            throw new IOException("Could not sign Garmin OAuth 1 request");
        } finally {
            request.disconnect();
        }
    }

    private JsonNode response(int status, String body) throws IOException {
        if (status < 200 || status >= 300) {
            boolean invalidGrant = false;
            try {
                invalidGrant = "invalid_grant".equals(mapper.readTree(body).path("error").asText());
            } catch (Exception ignored) {
                // Never include token endpoint response bodies in exceptions or logs.
            }
            throw new ApiException(status, invalidGrant);
        }
        try {
            JsonNode result = mapper.readTree(body);
            if (result == null || result.isNull()) {
                throw new IOException("Empty Garmin response");
            }
            return result;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IOException("Invalid Garmin JSON response");
        }
    }

    private static GarminTokens tokens(JsonNode body, Instant requestedAt) throws IOException {
        String access = requiredText(body, "access_token");
        String refresh = requiredText(body, "refresh_token");
        long expires = positiveSeconds(body, "expires_in");
        Instant refreshExpires = body.hasNonNull("refresh_token_expires_in")
                ? requestedAt.plusSeconds(positiveSeconds(body, "refresh_token_expires_in")) : null;
        if (body.hasNonNull("token_type") && !"bearer".equalsIgnoreCase(body.path("token_type").asText())) {
            throw new IOException("Unsupported Garmin token type");
        }
        return new GarminTokens(access, refresh, requestedAt.plusSeconds(expires), refreshExpires);
    }

    private static long positiveSeconds(JsonNode body, String field) throws IOException {
        JsonNode value = body.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() <= 0 || value.asLong() > 315_360_000) {
            throw new IOException("Invalid Garmin " + field);
        }
        return value.asLong();
    }

    private static String requiredText(JsonNode body, String field) throws IOException {
        JsonNode value = body.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IOException("Garmin response omitted " + field);
        }
        return value.asText();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static class ApiException extends IOException {
        private final int status;
        private final boolean invalidGrant;

        ApiException(int status, boolean invalidGrant) {
            super("Garmin returned HTTP " + status + (invalidGrant ? " (invalid_grant)" : ""));
            this.status = status;
            this.invalidGrant = invalidGrant;
        }

        public int status() { return status; }
        public boolean invalidGrant() { return invalidGrant; }
    }
}
