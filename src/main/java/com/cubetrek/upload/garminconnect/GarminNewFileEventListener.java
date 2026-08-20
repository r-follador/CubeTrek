package com.cubetrek.upload.garminconnect;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import com.cubetrek.upload.StorageService;
import com.cubetrek.upload.UploadResponse;
import lombok.Getter;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@Component
public class GarminNewFileEventListener implements ApplicationListener<GarminNewFileEventListener.OnEvent> {

    private static final Logger logger = LoggerFactory.getLogger(GarminNewFileEventListener.class);

    private final String garminConsumerKey;
    private final String garminConsumerSecret;
    private final UserThirdpartyConnectRepository userThirdpartyConnectRepository;
    private final GarminOAuth2TokenService garminOAuth2TokenService;
    private final StorageService storageService;

    public GarminNewFileEventListener(
            @Value("${garmin.consumer.key}") String garminConsumerKey,
            @Value("${garmin.consumer.secret}") String garminConsumerSecret,
            UserThirdpartyConnectRepository userThirdpartyConnectRepository,
            GarminOAuth2TokenService garminOAuth2TokenService,
            StorageService storageService) {
        this.garminConsumerKey = garminConsumerKey;
        this.garminConsumerSecret = garminConsumerSecret;
        this.userThirdpartyConnectRepository = userThirdpartyConnectRepository;
        this.garminOAuth2TokenService = garminOAuth2TokenService;
        this.storageService = storageService;
    }

    @Async
    @Override
    public void onApplicationEvent(OnEvent event) {
        downloadFile(event);
    }

    public void downloadFile(OnEvent event) {
        UserThirdpartyConnect connection = event.garminUserid != null
                ? userThirdpartyConnectRepository.findByGarminUserid(event.garminUserid)
                : userThirdpartyConnectRepository.findByGarminUseraccesstoken(event.legacyAccessToken);
        if (connection == null) {
            logger.error("Garmin Connect activity download failed: unknown connected account for activity {}", event.activityId);
            return;
        }

        Users user = connection.getUser();
        try {
            byte[] fileData = event.garminUserid != null
                    ? downloadWithOAuth2(connection, event.callbackURL)
                    : downloadWithOAuth1(connection, event.callbackURL);
            String filename = "garmin-push_" + event.activityId + "." + event.fileType;
            UploadResponse uploadResponse = storageService.store(user, fileData, filename);
            logger.info("Garmin Connect activity download successful: user={}, track={}", user.getId(), uploadResponse.getTrackID());
        } catch (ExceptionHandling.FileNotAccepted e) {
            logger.info("Garmin Connect activity ignored: user={}, activity={}, reason={}",
                    user.getId(), event.activityId, e.msg);
        } catch (IOException | InterruptedException | OAuthMessageSignerException | OAuthExpectationFailedException |
                 OAuthCommunicationException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("Garmin Connect activity download failed: user={}, activity={}", user.getId(), event.activityId, e);
        }
    }

    private byte[] downloadWithOAuth1(UserThirdpartyConnect connection, String callbackURL)
            throws IOException, OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException {
        OAuthConsumer consumer = new DefaultOAuthConsumer(garminConsumerKey, garminConsumerSecret);
        consumer.setTokenWithSecret(connection.getGarminUseraccesstoken(), connection.getGarminUseraccesstokenSecret());
        HttpURLConnection request = (HttpURLConnection) new URL(callbackURL).openConnection();
        request.setConnectTimeout(30_000);
        request.setReadTimeout(30_000);
        consumer.sign(request);
        try {
            return request.getInputStream().readAllBytes();
        } finally {
            request.disconnect();
        }
    }

    private byte[] downloadWithOAuth2(UserThirdpartyConnect connection, String callbackURL) throws IOException, InterruptedException {
        String accessToken = garminOAuth2TokenService.getValidAccessToken(connection);
        HttpURLConnection request = (HttpURLConnection) new URL(callbackURL).openConnection();
        request.setConnectTimeout(30_000);
        request.setReadTimeout(30_000);
        request.setRequestProperty("Authorization", "Bearer " + accessToken);
        try {
            int status = request.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Garmin activity callback returned HTTP " + status);
            }
            return request.getInputStream().readAllBytes();
        } finally {
            request.disconnect();
        }
    }

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
    }
}
