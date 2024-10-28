package com.cubetrek.upload.polaraccesslink;

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
public class PolarLoginEventListener implements ApplicationListener<PolarLoginEventListener.OnEvent> {

    Logger logger = LoggerFactory.getLogger(PolarLoginEventListener.class);

    @Value("${polar.client.id}")
    String polarConsumerKey;
    @Value("${polar.client.secret}")
    String polarConsumerSecret;

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;

    @Autowired
    PolarAccesslinkService polarAccesslinkService;

    @Async
    @Override
    public void onApplicationEvent(OnEvent event) {
        //we need to check regularly if the Polar user is still enabled
        //try to get polar user info; see https://github.com/polarofficial/accesslink-example-python/issues/28

        if (event.userThirdpartyConnect == null || !event.userThirdpartyConnect.isPolarEnabled()) {
            //if not enabled, then we don't care anymore
            return;
        }

        final String polarUserUrl = "https://www.polaraccesslink.com/v3/users/"+event.userThirdpartyConnect.getPolarUserid();
        try {
            HttpResponse<String> polaruserinfo = polarAccesslinkService.userTokenAuthenticationGET_getJSON(polarUserUrl, event.userThirdpartyConnect.getPolarUseraccesstoken());

            logger.info("Polar user info: " + polaruserinfo.body());
            if (polaruserinfo.statusCode()==401 || polaruserinfo.statusCode()==204) { //Authorization removed (401) or no Userid found (204)
                event.userThirdpartyConnect.setPolarEnabled(false);
                userThirdpartyConnectRepository.save(event.userThirdpartyConnect);
            }

        } catch (URISyntaxException | IOException | InterruptedException e) {
            logger.error("Error checking Polar Accesslink status", e);
        }
    }

    @Getter
    @Setter
    static public class OnEvent extends ApplicationEvent {
        UserThirdpartyConnect userThirdpartyConnect;

        public OnEvent(UserThirdpartyConnect userThirdpartyConnect) {
            super(userThirdpartyConnect);
            this.userThirdpartyConnect = userThirdpartyConnect;
        }
    }


}
