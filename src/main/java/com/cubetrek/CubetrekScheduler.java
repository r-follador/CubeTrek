package com.cubetrek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class CubetrekScheduler {
    Logger logger = LoggerFactory.getLogger(CubetrekScheduler.class);

    @CacheEvict(value = "publictracks", allEntries = true)
    @Scheduled(fixedRateString = "300000")
    public void emptypublictracksCache() {
        //Evict cache of publictracks every 5min, see TrackDataRepository.findPublicActivities
    }
}
