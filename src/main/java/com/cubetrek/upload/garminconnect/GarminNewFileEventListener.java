package com.cubetrek.upload.garminconnect;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.upload.StorageService;
import com.cubetrek.upload.UploadResponse;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GarminNewFileEventListener implements ApplicationListener<GarminNewFileEventListener.OnEvent> {
    private static final Logger logger = LoggerFactory.getLogger(GarminNewFileEventListener.class);
    private final UserThirdpartyConnectRepository repository;
    private final GarminOAuth2TokenService tokens;
    private final GarminActivityDownloader downloader;
    private final StorageService storage;

    public GarminNewFileEventListener(UserThirdpartyConnectRepository repository, GarminOAuth2TokenService tokens,
                                     GarminActivityDownloader downloader, StorageService storage) {
        this.repository = repository;
        this.tokens = tokens;
        this.downloader = downloader;
        this.storage = storage;
    }

    @Async
    @Override
    public void onApplicationEvent(OnEvent event) { downloadFile(event); }

    public void downloadFile(OnEvent event) {
        String auth = event.authenticationProtocol();
        UserThirdpartyConnect connection = event.garminUserid != null
                ? repository.findByGarminUserid(event.garminUserid)
                : repository.findByGarminUseraccesstoken(event.legacyAccessToken);
        if (connection == null) {
            logger.error("Garmin activity download failed: unknown connected account for activity {}, auth={}", event.activityId, auth);
            return;
        }
        if (!connection.isGarminEnabled()) {
            logger.info("Garmin activity ignored: permission disabled, connection={}, auth={}", connection.getId(), auth);
            return;
        }
        try {
            downloader.validate(event.callbackURL);
            byte[] file = downloadWithRetry(connection, event);
            UploadResponse result = storage.store(connection.getUser(), file, "garmin-push_" + event.activityId + "." + event.fileType);
            logger.info("Garmin Connect activity download successful: user={}, track={}, auth={}",
                    connection.getUser().getId(), result.getTrackID(), auth);
        } catch (ExceptionHandling.FileNotAccepted e) {
            logger.info("Garmin Connect activity ignored: user={}, activity={}, reason={}, auth={}",
                    connection.getUser().getId(), event.activityId, e.msg, auth);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Garmin activity download interrupted: connection={}, activity={}, auth={}",
                    connection.getId(), event.activityId, auth);
        } catch (Exception e) {
            // URL-bearing transport exceptions must not put callback tokens into logs.
            logger.error("Garmin activity download failed: connection={}, activity={}, errorType={}, auth={}",
                    connection.getId(), event.activityId, e.getClass().getSimpleName(), auth);
        }
    }

    private byte[] downloadWithRetry(UserThirdpartyConnect connection, OnEvent event) throws IOException, InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                String bearer = event.garminUserid == null ? null : tokens.getValidAccessToken(connection.getId());
                return downloader.download(event.callbackURL, bearer, connection.getGarminUseraccesstoken(), connection.getGarminUseraccesstokenSecret());
            } catch (IOException e) {
                if (attempt >= 3 || (e instanceof GarminActivityDownloader.DownloadException failure && !failure.retryable())
                        || (e instanceof GarminApiClient.ApiException failure && failure.status() != 429 && failure.status() < 500)) throw e;
                logger.warn("Retrying Garmin activity download: connection={}, attempt={}, auth={}",
                        connection.getId(), attempt, event.authenticationProtocol());
                pauseBeforeRetry(attempt);
            }
        }
    }

    void pauseBeforeRetry(int attempt) throws InterruptedException { Thread.sleep(attempt * 2000L); }

    @Getter
    public static class OnEvent extends ApplicationEvent {
        private final String activityId;
        private final String garminUserid;
        private final String legacyAccessToken;
        private final String callbackURL;
        private final String fileType;

        public OnEvent(String activityId, String garminUserid, String legacyAccessToken, String callbackURL, String fileType) {
            super(garminUserid != null ? garminUserid : legacyAccessToken);
            this.activityId = activityId;
            this.garminUserid = garminUserid;
            this.legacyAccessToken = legacyAccessToken;
            this.callbackURL = callbackURL;
            this.fileType = fileType;
        }

        private String authenticationProtocol() { return garminUserid == null ? "OAuth1" : "OAuth2"; }

        @Override
        public String toString() { return "GarminActivityNotification[redacted]"; }
    }
}
