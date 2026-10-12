package com.cubetrek.upload.garminconnect;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import com.cubetrek.upload.StorageService;
import com.cubetrek.upload.UploadResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GarminDownloadTest {
    private static final String CALLBACK = "https://apis.garmin.com/activity/file?token=opaque%2Bvalue&x=1";
    private final Logger logger = (Logger) LoggerFactory.getLogger(GarminNewFileEventListener.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachLogs() {
        logger.detachAppender(logs);
        logs.stop();
    }

    private String messages() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successfulDownloadLogsTheAuthenticationActuallySelected(boolean oauth2) throws Exception {
        var fixture = new Fixture();
        byte[] fit = {1, 2};
        String bearer = oauth2 ? "private-bearer-token" : null;
        if (oauth2) when(fixture.tokens.getValidAccessToken(7L)).thenReturn(bearer);
        when(fixture.downloader.download(CALLBACK, bearer, "oauth1", "secret")).thenReturn(fit);
        var result = new UploadResponse();
        result.setTrackID(1234L);
        when(fixture.storage.store(fixture.user, fit, "garmin-push_123.FIT")).thenReturn(result);

        fixture.listener.downloadFile(fixture.event(oauth2 ? "stable-id" : null, oauth2 ? null : "oauth1"));

        assertTrue(messages().contains("activity download successful: user=42, track=1234, auth=" + (oauth2 ? "OAuth2" : "OAuth1")));
        assertFalse(messages().contains(CALLBACK));
        assertFalse(messages().contains("opaque%2Bvalue"));
        assertFalse(messages().contains("private-bearer-token"));
        assertFalse(messages().contains("secret"));
        assertFalse(messages().contains("oauth1"));
        verify(fixture.downloader).download(CALLBACK, bearer, "oauth1", "secret");
        if (!oauth2) verifyNoInteractions(fixture.tokens);
    }

    @Test
    void retryAndFailureLogsLabelOauth2WithoutLeakingTransportExceptionDetails() throws Exception {
        var fixture = new Fixture();
        when(fixture.tokens.getValidAccessToken(7L)).thenReturn("private-bearer-token");
        when(fixture.downloader.download(CALLBACK, "private-bearer-token", "oauth1", "secret"))
                .thenThrow(new IOException(CALLBACK + " private-bearer-token"));

        fixture.listener.downloadFile(fixture.event("stable-id", null));

        assertTrue(messages().contains("Retrying Garmin activity download: connection=7, attempt=1, auth=OAuth2"));
        assertTrue(messages().contains("activity download failed: connection=7, activity=123, errorType=IOException, auth=OAuth2"));
        assertFalse(messages().contains(CALLBACK));
        assertFalse(messages().contains("private-bearer-token"));
        assertTrue(logs.list.stream().allMatch(event -> event.getThrowableProxy() == null));
        verifyNoInteractions(fixture.storage);
    }

    @Test
    void callbackUrlIsPreservedWithBearerAndWithoutRedirects() throws Exception {
        var downloader = spy(new GarminActivityDownloader("consumer", "secret", "apis.garmin.com"));
        var http = mock(HttpURLConnection.class);
        doReturn(http).when(downloader).openConnection(URI.create(CALLBACK));
        when(http.getResponseCode()).thenReturn(200);
        when(http.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2}));
        assertArrayEquals(new byte[]{1, 2}, downloader.download(CALLBACK, "bearer", null, null));
        verify(downloader).openConnection(URI.create(CALLBACK));
        verify(http).setRequestProperty("Authorization", "Bearer bearer");
        verify(http).setInstanceFollowRedirects(false);
        verify(http).disconnect();
    }

    @Test
    void refusesUntrustedCallbackDestinationsBeforeOpeningConnection() throws Exception {
        var downloader = spy(new GarminActivityDownloader("consumer", "secret", "apis.garmin.com"));
        for (String url : new String[]{"http://apis.garmin.com/file", "https://apis.garmin.com.evil.test/file",
                "https://apis.garmin.com@evil.test/file", "https://localhost/file", "https://apis.garmin.com:8443/file",
                "https://apis.garmin.com/file#token", "https://user@apis.garmin.com/file"}) {
            assertThrows(IOException.class, () -> downloader.download(url, "bearer", null, null));
        }
        verify(downloader, never()).openConnection(any());
    }

    @Test
    void retriesTransientDownloadsButStoresOnlyOnce() throws Exception {
        var fixture = new Fixture();
        byte[] fit = {1, 2};
        when(fixture.tokens.getValidAccessToken(7L)).thenReturn("bearer");
        when(fixture.downloader.download(CALLBACK, "bearer", "oauth1", "secret"))
                .thenThrow(new GarminActivityDownloader.DownloadException(503))
                .thenThrow(new GarminActivityDownloader.DownloadException(429)).thenReturn(fit);
        when(fixture.storage.store(fixture.user, fit, "garmin-push_123.FIT")).thenReturn(new UploadResponse());
        fixture.listener.downloadFile(fixture.event("stable-id", null));
        verify(fixture.downloader, times(3)).download(CALLBACK, "bearer", "oauth1", "secret");
        verify(fixture.storage).store(fixture.user, fit, "garmin-push_123.FIT");
    }

    @Test
    void oauth1NoGpsFileIsNotRetriedAndDoesNotUseOauth2() throws Exception {
        var fixture = new Fixture();
        byte[] fit = {1, 2};
        when(fixture.downloader.download(CALLBACK, null, "oauth1", "secret")).thenReturn(fit);
        when(fixture.storage.store(fixture.user, fit, "garmin-push_123.FIT"))
                .thenThrow(new ExceptionHandling.FileNotAccepted("FIT fixstatus:201"));
        fixture.listener.downloadFile(fixture.event(null, "oauth1"));
        verifyNoInteractions(fixture.tokens);
        verify(fixture.downloader).download(CALLBACK, null, "oauth1", "secret");
        verify(fixture.listener, never()).pauseBeforeRetry(anyInt());
        assertTrue(messages().contains("activity ignored: user=42, activity=123, reason=FIT fixstatus:201, auth=OAuth1"));
    }

    @Test
    void permanentlyFailedDownloadIsNotRetried() throws Exception {
        var fixture = new Fixture();
        when(fixture.downloader.download(CALLBACK, null, "oauth1", "secret"))
                .thenThrow(new GarminActivityDownloader.DownloadException(404));
        fixture.listener.downloadFile(fixture.event(null, "oauth1"));
        verify(fixture.downloader).download(CALLBACK, null, "oauth1", "secret");
        verifyNoInteractions(fixture.storage);
        verify(fixture.listener, never()).pauseBeforeRetry(anyInt());
    }

    @Test
    void permissionOptOutKeepsTokensButDeregistrationClearsThem() throws Exception {
        var fixture = new Fixture();
        fixture.connection.setGarminOauth2RefreshToken("refresh");
        when(fixture.repository.lockById(7L)).thenReturn(Optional.of(fixture.connection));
        var listener = new GarminNewDeregistrationEventListener(fixture.repository);
        listener.onApplicationEvent(new GarminNewDeregistrationEventListener.OnEvent("stable-id", null, false, false));
        assertFalse(fixture.connection.isGarminEnabled());
        assertEquals("refresh", fixture.connection.getGarminOauth2RefreshToken());
        listener.onApplicationEvent(new GarminNewDeregistrationEventListener.OnEvent("stable-id", null, false, true));
        assertNull(fixture.connection.getGarminOauth2RefreshToken());
        assertNull(fixture.connection.getGarminUseraccesstoken());
        assertEquals("stable-id", fixture.connection.getGarminUserid());
    }

    private static class Fixture {
        final UserThirdpartyConnectRepository repository = mock(UserThirdpartyConnectRepository.class);
        final GarminOAuth2TokenService tokens = mock(GarminOAuth2TokenService.class);
        final GarminActivityDownloader downloader = mock(GarminActivityDownloader.class);
        final StorageService storage = mock(StorageService.class);
        final GarminNewFileEventListener listener = spy(new GarminNewFileEventListener(repository, tokens, downloader, storage));
        final UserThirdpartyConnect connection = new UserThirdpartyConnect();
        final Users user = new Users();

        Fixture() throws Exception {
            ReflectionTestUtils.setField(user, "id", 42L);
            connection.setId(7L);
            connection.setUser(user);
            connection.setGarminUserid("stable-id");
            connection.setGarminEnabled(true);
            connection.setGarminUseraccesstoken("oauth1");
            connection.setGarminUseraccesstokenSecret("secret");
            when(repository.findByGarminUserid("stable-id")).thenReturn(connection);
            when(repository.findByGarminUseraccesstoken("oauth1")).thenReturn(connection);
            doNothing().when(listener).pauseBeforeRetry(anyInt());
        }

        GarminNewFileEventListener.OnEvent event(String userId, String token) {
            return new GarminNewFileEventListener.OnEvent("123", userId, token, CALLBACK, "FIT");
        }
    }
}
