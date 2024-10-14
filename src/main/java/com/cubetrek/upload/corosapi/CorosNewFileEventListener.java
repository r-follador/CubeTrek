package com.cubetrek.upload.corosapi;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import com.cubetrek.upload.StorageService;
import com.cubetrek.upload.UploadResponse;
import com.cubetrek.upload.polaraccesslink.PolarAccesslinkService;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;


@Component
public class CorosNewFileEventListener implements ApplicationListener<CorosNewFileEventListener.OnEvent> {

    Logger logger = LoggerFactory.getLogger(CorosNewFileEventListener.class);

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;
    @Autowired
    private StorageService storageService;

    @Autowired
    PolarAccesslinkService polarAccesslinkService;

    @Async
    @Override
    public void onApplicationEvent(OnEvent event) {
        this.downloadFile(event);
    }

    public void downloadFile(OnEvent event) {

        if (!event.getUrl().contains("polaraccesslink.com")) {
            logger.error("PolarAccesslink: will not download file, wrong URL: "+event.getUrl()+"; Payload: "+event.getPayload());
            return;
        }

        UserThirdpartyConnect userThirdpartyConnect = userThirdpartyConnectRepository.findByPolarUserid(event.getUserId());
        if (userThirdpartyConnect == null){
            logger.error("PolarAccesslink: pull file failed: Unknown User id: "+event.getUserId()+"; Payload: "+event.getPayload());
            return;
        }
        Users user = userThirdpartyConnect.getUser();

        String downloadUrl = event.getUrl()+"/fit";
        UploadResponse uploadResponse = null;
        try {
            HttpResponse<InputStream> response = polarAccesslinkService.userTokenAuthenticationGET_getFile(downloadUrl, userThirdpartyConnect.getPolarUseraccesstoken());
            if (response.statusCode() == 200) {
                byte[] filedata = response.body().readAllBytes();
                response.body().close();
                String filename = "polar-"+event.entityId+".FIT";
                uploadResponse = storageService.store(user, filedata, filename);
            } else {
                logger.error("PolarAccesslink: pull file failed: User id: "+user.getId()+", User Polar ID '"+event.getUserId()+"', CallbackURL '"+event.getUrl()+"'");
                logger.error("Response status code: "+response.statusCode()+"; Payload: "+event.getPayload());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            logger.error("PolarAccesslink: pull file failed: User id: "+user.getId()+", User Polar ID '"+event.getUserId()+"', CallbackURL '"+event.getUrl()+"'"+"; Payload: "+event.getPayload());
            logger.error("PolarAccesslink", e);
        } catch (ExceptionHandling.FileNotAccepted e) {
            //Already logged in StorageService, do nothing
        }

        if (uploadResponse != null)
            logger.info("PolarAccesslink: pull file successful: User id: "+user.getId()+"; Track ID: "+uploadResponse.getTrackID());
        else
            logger.error("PolarAccesslink: pull file failed: User id: "+user.getId()+", User Polar Id '"+event.userId+"', CallbackURL '"+event.getUrl()+"'"+"; Payload: "+event.getPayload());
    }

    @Getter
    @Setter
    static public class OnEvent extends ApplicationEvent {
        String entityId;
        String userId;
        String url;
        String payload;

        public OnEvent(String entityId, String userId, String url, String payload) {
            super(userId);
            this.entityId = entityId;
            this.userId = userId;
            this.url = url;
            this.payload = payload;
        }
    }


}
