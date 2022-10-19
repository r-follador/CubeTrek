package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.*;
import com.cubetrek.registration.OnRegistrationCompleteEvent;
import com.cubetrek.registration.RegistrationListener;
import com.cubetrek.upload.StorageService;
import com.cubetrek.upload.UploadResponse;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.TimeZone;


@Component
public class GarminNewFileEventListener implements ApplicationListener<OnNewGarminFileEvent> {

    Logger logger = LoggerFactory.getLogger(GarminNewFileEventListener.class);

    @Value("${garmin.consumer.key}")
    String garminConsumerKey;
    @Value("${garmin.consumer.secret}")
    String garminConsumerSecret;

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;
    @Autowired
    private StorageService storageService;

    @Override
    public void onApplicationEvent(OnNewGarminFileEvent event) {
        this.downloadFile(event);
    }

    private void downloadFile(OnNewGarminFileEvent event) {
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

            uploadResponse = storageService.store(user, filedata, filename, TimeZone.getDefault(), TrackData.Sharing.PRIVATE);

        } catch (IOException | OAuthMessageSignerException | OAuthExpectationFailedException |
                 OAuthCommunicationException e) {
            logger.error("GarminConnect: pull file failed: User id: "+user.getId()+", UserAccessToken '"+event.getUserAccessToken()+"', CallbackURL '"+event.getCallbackURL()+"'");
            logger.error("GarminConnect", e);
            return;
        }

        if (uploadResponse != null)
            logger.info("GarminConnect: pull file successful: User id: "+user.getId()+"; Track ID: "+uploadResponse.getTrackID());
        else
            logger.error("GarminConnect: pull file failed: User id: "+user.getId()+", UserAccessToken '"+event.getUserAccessToken()+"', CallbackURL '"+event.getCallbackURL()+"'");
    }


}
