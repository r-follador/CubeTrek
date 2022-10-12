package com.cubetrek.upload.garminconnect;

import com.cubetrek.registration.OnRegistrationCompleteEvent;
import com.cubetrek.registration.RegistrationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class GarminNewFileEventListener implements ApplicationListener<OnNewGarminFileEvent> {

    Logger logger = LoggerFactory.getLogger(GarminNewFileEventListener.class);

    @Override
    public void onApplicationEvent(OnNewGarminFileEvent event) {
        this.downloadFile(event);
    }

    private void downloadFile(OnNewGarminFileEvent event) {

        logger.info("DownloadGarminfile: userid: " + event.getUserid() + "/ U" + "seraccesstoken: " + event.getUserAccessToken());

        //TODO: download file
    }


}
