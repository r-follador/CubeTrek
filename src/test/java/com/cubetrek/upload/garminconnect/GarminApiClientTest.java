package com.cubetrek.upload.garminconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GarminApiClientTest {
    private final HttpClient http = mock(HttpClient.class);
    @SuppressWarnings("unchecked")
    private final HttpResponse<String> response = mock(HttpResponse.class);
    private final GarminApiClient api = new GarminApiClient("consumer", "secret", "client", "client-secret", new ObjectMapper(), http);

    @BeforeEach
    void setup() throws Exception {
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
    }

    @Test
    void codeExchangeUsesBasicAuthenticationAndPkceForm() throws Exception {
        when(response.body()).thenReturn("""
                {"access_token":"access","refresh_token":"refresh","token_type":"bearer",
                 "expires_in":3600,"refresh_token_expires_in":7776000}
                """);
        Instant before = Instant.now();
        GarminTokens tokens = api.exchangeCode("code+value", "verifier", "https://cubetrek.test/callback");
        assertEquals("refresh", tokens.refreshToken());
        assertTrue(tokens.accessExpiresAt().isAfter(before.plusSeconds(3599)));
        assertTrue(tokens.refreshExpiresAt().isAfter(before.plusSeconds(7775999)));
        HttpRequest request = request();
        assertEquals("https://diauth.garmin.com/di-oauth2-service/oauth/token", request.uri().toString());
        assertEquals("Basic " + Base64.getEncoder().encodeToString("client:client-secret".getBytes(StandardCharsets.UTF_8)),
                request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("POST", request.method());
        String body = body(request);
        assertTrue(body.contains("code=code%2Bvalue"));
        assertTrue(body.contains("code_verifier=verifier"));
        assertTrue(body.contains("grant_type=authorization_code"));
        assertTrue(body.contains("redirect_uri=https%3A%2F%2Fcubetrek.test%2Fcallback"));
    }

    @Test
    void refreshRequiresRotatedRefreshTokenInResponse() {
        when(response.body()).thenReturn("{\"access_token\":\"access\",\"expires_in\":3600}");
        assertThrows(IOException.class, () -> api.refresh("old-refresh"));
    }

    @Test
    void userIdUsesBearer() throws Exception {
        when(response.body()).thenReturn("{\"userId\":\"stable-id\"}");
        assertEquals("stable-id", api.userId("access"));
        assertEquals("Bearer access", request().headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void permissionResponseAcceptsLiveWrapperAndMatchesExactPermission() throws Exception {
        when(response.body()).thenReturn("{\"permissions\":[\"ACTIVITY_EXPORT\"]}",
                "{\"permissions\":[]}", "{\"permissions\":[\"NOT_ACTIVITY_EXPORT\"]}");
        assertTrue(api.activityExportAllowed("access"));
        assertFalse(api.activityExportAllowed("access"));
        assertFalse(api.activityExportAllowed("access"));
    }

    @Test
    void permissionResponseAlsoAcceptsBareArray() throws Exception {
        when(response.body()).thenReturn("[\"ACTIVITY_EXPORT\"]", "[]", "[\"NOT_ACTIVITY_EXPORT\"]");
        assertTrue(api.activityExportAllowed("access"));
        assertFalse(api.activityExportAllowed("access"));
        assertFalse(api.activityExportAllowed("access"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"permissions\":null}", "{\"permissions\":\"ACTIVITY_EXPORT\"}",
            "{\"permissions\":{}}", "{\"permissions\":[\"ACTIVITY_EXPORT\",null]}",
            "{\"permissions\":[1]}", "[\"ACTIVITY_EXPORT\",{}]", "\"ACTIVITY_EXPORT\""})
    void malformedPermissionsFailRatherThanGrantingOrRevokingAccess(String body) {
        when(response.body()).thenReturn(body);
        assertThrows(IOException.class, () -> api.activityExportAllowed("access"));
    }

    @Test
    void apiErrorDoesNotExposeResponseSecrets() {
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("{\"error\":\"invalid_grant\",\"error_description\":\"private-refresh-token\"}");
        var failure = assertThrows(GarminApiClient.ApiException.class, () -> api.refresh("refresh"));
        assertTrue(failure.invalidGrant());
        assertEquals(400, failure.status());
        assertFalse(failure.toString().contains("private-refresh-token"));
    }

    @Test
    void malformedSuccessDoesNotExposePayload() {
        when(response.body()).thenReturn("private-access-token not JSON");
        var failure = assertThrows(IOException.class, () -> api.userId("access"));
        assertFalse(failure.toString().contains("private-access-token"));
        assertNull(failure.getCause());
    }

    private HttpRequest request() throws Exception {
        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        return captor.getValue();
    }

    private static String body(HttpRequest request) {
        StringBuilder result = new StringBuilder();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer data) { result.append(StandardCharsets.UTF_8.decode(data)); }
            public void onError(Throwable error) { fail(error); }
            public void onComplete() { }
        });
        return result.toString();
    }
}
