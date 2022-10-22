package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.TrackData;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import com.cubetrek.upload.StorageService;
import com.cubetrek.upload.UploadResponse;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.TimeZone;


@Component
public class GarminNewDeregistrationEventListener implements ApplicationListener<OnNewGarminDeregistrationEvent> {

    Logger logger = LoggerFactory.getLogger(GarminNewDeregistrationEventListener.class);

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;

    @Async
    @Override
    public void onApplicationEvent(OnNewGarminDeregistrationEvent event) {
        this.deregUser(event);
    }

    public void deregUser(OnNewGarminDeregistrationEvent event) {
        UserThirdpartyConnect userThirdpartyConnect = userThirdpartyConnectRepository.findByGarminUseraccesstoken(event.getUserAccessToken());
        if (userThirdpartyConnect == null){
            logger.error("GarminConnect: deregistration/permission change failed: Unknown Useracccestoken: "+event.getUserAccessToken());
            return;
        }

        userThirdpartyConnect.setGarminEnabled(event.isEnabled);
        userThirdpartyConnectRepository.save(userThirdpartyConnect);
        logger.info("GarminConnect: User id '"+userThirdpartyConnect.getUser().getId()+"' changed permission to "+(event.isEnabled?"ENABLED":"DISABLED"));
    }


}
