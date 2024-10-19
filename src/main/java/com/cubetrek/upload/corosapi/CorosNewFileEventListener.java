package com.cubetrek.upload.corosapi;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import com.cubetrek.upload.StorageService;
import com.cubetrek.upload.UploadResponse;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;


@Component
public class CorosNewFileEventListener implements ApplicationListener<CorosNewFileEventListener.OnEvent> {

    Logger logger = LoggerFactory.getLogger(CorosNewFileEventListener.class);

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

        if (!event.getUrl().contains(".fit")) {
            logger.error("Coros: will not download file, no Fit extenstion: "+event.getUrl());
            return;
        }

        UserThirdpartyConnect userThirdpartyConnect = userThirdpartyConnectRepository.findByCorosUserid(event.getUserId());
        if (userThirdpartyConnect == null){
            logger.error("Coros: pull file failed: Unknown User id: "+event.getUserId());
            return;
        }
        Users user = userThirdpartyConnect.getUser();
        String filename = "coros-"+event.getUrl().substring(event.getUrl().lastIndexOf('/') + 1);
        UploadResponse uploadResponse = null;
        try {

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<byte[]> response = restTemplate.getForEntity(event.getUrl(), byte[].class);

            if (response.getStatusCode().is2xxSuccessful()) {
                uploadResponse = storageService.store(user, Objects.requireNonNull(response.getBody()), filename);
            } else {
                logger.error("Coros: pull file failed: User id: "+user.getId()+", User Coros ID '"+event.getUserId()+"', File Url '"+event.getUrl()+"'");
                logger.error("Response status code: "+response.getStatusCode());
            }
        } catch (NullPointerException e) {
            logger.error("Coros: pull file failed: User id: "+user.getId()+", User Polar ID '"+event.getUserId()+"', File Url '"+event.getUrl()+"'");
            logger.error("Coros", e);
        } catch (ExceptionHandling.FileNotAccepted e) {
            //Already logged in StorageService, do nothing
        }

        if (uploadResponse != null)
            logger.info("Coros: download file successful: User id: "+user.getId()+"; Track ID: "+uploadResponse.getTrackID());
        else
            logger.error("Coros: download file failed: User id: "+user.getId()+", User Polar Id '"+event.userId+"', CallbackURL '"+event.getUrl()+"'");
    }

    @Getter
    @Setter
    static public class OnEvent extends ApplicationEvent {
        String userId;
        String url;

        public OnEvent(String userId, String url) {
            super(userId);
            this.userId = userId;
            this.url = url;
        }
    }


}
