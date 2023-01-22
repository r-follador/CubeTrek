package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;


@Component
public class GarminNewDeregistrationEventListener implements ApplicationListener<GarminNewDeregistrationEventListener.OnEvent> {

    Logger logger = LoggerFactory.getLogger(GarminNewDeregistrationEventListener.class);

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;

    @Async
    @Override
    public void onApplicationEvent(OnEvent event) {
        this.deregUser(event);
    }

    public void deregUser(OnEvent event) {
        UserThirdpartyConnect userThirdpartyConnect = userThirdpartyConnectRepository.findByGarminUseraccesstoken(event.getUserAccessToken());
        if (userThirdpartyConnect == null){
            logger.error("GarminConnect: deregistration/permission change failed: Unknown Useracccestoken: "+event.getUserAccessToken()+"; Payload: "+event.getPayload());
            return;
        }

        userThirdpartyConnect.setGarminEnabled(event.isEnabled);
        userThirdpartyConnectRepository.save(userThirdpartyConnect);
        logger.info("GarminConnect: User id '"+userThirdpartyConnect.getUser().getId()+"' changed permission to "+(event.isEnabled?"ENABLED":"DISABLED"));
    }

    @Getter
    @Setter
    public static class OnEvent extends ApplicationEvent {

        String userAccessToken;

        boolean isEnabled;

        String payload;

        public OnEvent(String userAccessToken, boolean isEnabled, String payload) {
            super(userAccessToken);
            this.userAccessToken = userAccessToken;
            this.isEnabled = isEnabled;
            this.payload = payload;
        }
    }

}
