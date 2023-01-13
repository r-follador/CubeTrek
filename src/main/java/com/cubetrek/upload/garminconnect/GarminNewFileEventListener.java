package com.cubetrek.upload.garminconnect;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.*;
import com.cubetrek.upload.StorageService;
import com.cubetrek.upload.UploadResponse;
import lombok.Getter;
import lombok.Setter;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    Logger logger = LoggerFactory.getLogger(GarminNewFileEventListener.class);

    @Value("${garmin.consumer.key}")
    String garminConsumerKey;
    @Value("${garmin.consumer.secret}")
    String garminConsumerSecret;

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;
    @Autowired
    private StorageService storageService;

    @Async
    @Override
    public void onApplicationEvent(OnEvent event) {
        this.downloadFile(event);
    }

    public void downloadFile(OnEvent event) {
        UserThirdpartyConnect userThirdpartyConnect = userThirdpartyConnectRepository.findByGarminUseraccesstoken(event.getUserAccessToken());
        if (userThirdpartyConnect == null){
            logger.error("GarminConnect: pull file failed: Unknown Useracccestoken: "+event.getUserAccessToken());
            return;
        }
        Users user = userThirdpartyConnect.getUser();

        OAuthConsumer consumer = new DefaultOAuthConsumer(garminConsumerKey, garminConsumerSecret);
        consumer.setTokenWithSecret(userThirdpartyConnect.getGarminUseraccesstoken(), userThirdpartyConnect.getGarminUseraccesstokenSecret());
        HttpURLConnection request;
        UploadResponse uploadResponse = null;
        try {
            URL url = new URL(event.getCallbackURL());
            request = (HttpURLConnection) url.openConnection();
            consumer.sign(request);

            byte[] filedata = request.getInputStream().readAllBytes();
            String filename = "garmin-push_"+event.getActivityId()+"."+event.getFileType();

            uploadResponse = storageService.store(user, filedata, filename);

        } catch (IOException | OAuthMessageSignerException | OAuthExpectationFailedException |
                 OAuthCommunicationException e) {
            logger.error("GarminConnect: pull file failed: User id: "+user.getId()+", UserAccessToken '"+event.getUserAccessToken()+"', CallbackURL '"+event.getCallbackURL()+"'");
            logger.error("GarminConnect", e);
            return;
        } catch (ExceptionHandling.FileNotAccepted e) {
            //Already loggged in StorageService, do nothing
        }

        if (uploadResponse != null)
            logger.info("GarminConnect: pull file successful: User id: "+user.getId()+"; Track ID: "+uploadResponse.getTrackID());
        else
            logger.error("GarminConnect: pull file failed: User id: "+user.getId()+", UserAccessToken '"+event.getUserAccessToken()+"', CallbackURL '"+event.getCallbackURL()+"'");
    }

    @Getter
    @Setter
    public static class OnEvent extends ApplicationEvent {
        String activityId;
        String userAccessToken;
        String callbackURL;
        String fileType;

        public OnEvent(String activityId, String userAccessToken, String callbackURL, String fileType) {
            super(userAccessToken);
            this.activityId = activityId;
            this.userAccessToken = userAccessToken;
            this.callbackURL = callbackURL;
            this.fileType = fileType;
        }
    }


}
