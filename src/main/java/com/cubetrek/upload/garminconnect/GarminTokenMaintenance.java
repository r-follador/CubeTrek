package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.UserThirdpartyConnectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class GarminTokenMaintenance {
    private static final Logger logger = LoggerFactory.getLogger(GarminTokenMaintenance.class);
    private final UserThirdpartyConnectRepository repository;
    private final GarminOAuth2TokenService tokens;

    public GarminTokenMaintenance(UserThirdpartyConnectRepository repository, GarminOAuth2TokenService tokens) {
        this.repository = repository;
        this.tokens = tokens;
    }

    @Scheduled(cron = "${garmin.oauth2.keep-alive-cron:0 0 3 * * *}", zone = "UTC")
    public void refreshExpiringConnections() {
        for (Long id : repository.findGarminRefreshCandidates(Instant.now().plus(GarminOAuth2TokenService.KEEP_ALIVE_MARGIN))) {
            try {
                tokens.keepAlive(id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                logger.warn("Garmin token maintenance failed: connection={}, errorType={}", id, e.getClass().getSimpleName());
            }
        }
    }
}
