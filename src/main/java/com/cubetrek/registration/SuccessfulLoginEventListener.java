package com.cubetrek.registration;

import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import com.cubetrek.database.UsersRepository;
import com.cubetrek.upload.polaraccesslink.PolarAccesslinkService;
import com.cubetrek.upload.polaraccesslink.PolarLoginEventListener;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
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
    ApplicationEventPublisher eventPublisher;

    @Async
    @Override
    public void onApplicationEvent(OnEvent event) {
        this.onLogin(event);
    }

    public void onLogin(OnEvent event) {
        logger.info("Login of user id '" + event.getUser().getId()+"'; Name: '"+event.getUser().getName()+"'");

        //Check if Polar is still connected
        UserThirdpartyConnect userThirdpartyConnect = userThirdpartyConnectRepository.findByUser(event.getUser());
        if (userThirdpartyConnect != null && userThirdpartyConnect.isPolarEnabled()) {
            eventPublisher.publishEvent(new PolarLoginEventListener.OnEvent(userThirdpartyConnect));
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
