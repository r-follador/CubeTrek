package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
public class GarminNewDeregistrationEventListener implements ApplicationListener<GarminNewDeregistrationEventListener.OnEvent> {

    private static final Logger logger = LoggerFactory.getLogger(GarminNewDeregistrationEventListener.class);

    private final UserThirdpartyConnectRepository userThirdpartyConnectRepository;

    public GarminNewDeregistrationEventListener(UserThirdpartyConnectRepository userThirdpartyConnectRepository) {
        this.userThirdpartyConnectRepository = userThirdpartyConnectRepository;
    }

    @Async
    @Override
    @Transactional
    public void onApplicationEvent(OnEvent event) {
        deregUser(event);
    }

    public void deregUser(OnEvent event) {
        UserThirdpartyConnect connection = event.garminUserid != null
                ? userThirdpartyConnectRepository.findByGarminUserid(event.garminUserid)
                : userThirdpartyConnectRepository.findByGarminUseraccesstoken(event.legacyAccessToken);
        if (connection == null) {
            logger.error("Garmin Connect deregistration/permission change failed: unknown connected account");
            return;
        }

        connection = userThirdpartyConnectRepository.lockById(connection.getId()).orElseThrow();
        if (event.garminUserid != null ? !Objects.equals(event.garminUserid, connection.getGarminUserid())
                : !Objects.equals(event.legacyAccessToken, connection.getGarminUseraccesstoken())) return;

        connection.setGarminEnabled(event.enabled);
        if (event.deregistration) {
            GarminTokens.clear(connection);
            connection.setGarminUseraccesstoken(null);
            connection.setGarminUseraccesstokenSecret(null);
        }
        userThirdpartyConnectRepository.save(connection);
        logger.info("Garmin Connect user {} changed activity-export permission to {}",
                connection.getUser().getId(), event.enabled ? "enabled" : "disabled");
    }

    @Getter
    public static class OnEvent extends ApplicationEvent {

        private final String garminUserid;
        private final String legacyAccessToken;
        private final boolean enabled;

        private final boolean deregistration;

        public OnEvent(String garminUserid, String legacyAccessToken, boolean enabled) {
            this(garminUserid, legacyAccessToken, enabled, false);
        }

        public OnEvent(String garminUserid, String legacyAccessToken, boolean enabled, boolean deregistration) {
            super(garminUserid != null ? garminUserid : legacyAccessToken);
            this.garminUserid = garminUserid;
            this.legacyAccessToken = legacyAccessToken;
            this.enabled = enabled;
            this.deregistration = deregistration;
        }

        @Override
        public String toString() { return "GarminPermissionNotification[redacted]"; }
    }
}
