package com.cubetrek.registration;

import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import com.cubetrek.database.UsersRepository;
import com.cubetrek.upload.polaraccesslink.PolarAccesslinkService;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;


@Component
public class SuccessfulLoginEventListener implements ApplicationListener<SuccessfulLoginEventListener.OnEvent> {

    Logger logger = LoggerFactory.getLogger(SuccessfulLoginEventListener.class);

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;

    @Autowired
    PolarAccesslinkService polarAccesslinkService;

    @Async
    @Override
    public void onApplicationEvent(OnEvent event) {
        this.onLogin(event);
    }

    public void onLogin(OnEvent event) {
        logger.info("Login of user id '" + event.getUser().getId()+"'; Name: '"+event.getUser().getName()+"'");

        //Check if Polar is still connected
        UserThirdpartyConnect userThirdpartyConnect = userThirdpartyConnectRepository.findByUser(event.getUser());
        if (userThirdpartyConnect.isPolarEnabled()) {
            //try to get polar user info in order to check if still enabled; see https://github.com/polarofficial/accesslink-example-python/issues/28
            final String polarUserUrl = "https://www.polaraccesslink.com/v3/users/"+userThirdpartyConnect.getPolarUserid();
            try {
                HttpResponse<String> polaruserinfo = polarAccesslinkService.userTokenAuthenticationGET_getJSON(polarUserUrl, userThirdpartyConnect.getPolarUseraccesstoken());

                logger.info("Polar user info: " + polaruserinfo.body());
                if (polaruserinfo.statusCode()==401 || polaruserinfo.statusCode()==204) { //Authorization removed (401) or no Userid found (204)
                    userThirdpartyConnect.setPolarEnabled(false);
                    userThirdpartyConnectRepository.save(userThirdpartyConnect);
                }

            } catch (URISyntaxException | IOException | InterruptedException e) {
                logger.error("Error checking Polar Accesslink status", e);
            }
        }

    }

    @Getter
    @Setter
    public static class OnEvent extends ApplicationEvent {
        Users user;

        public OnEvent(Users user) {
            super(user);
            this.user = user;
        }
    }
}
