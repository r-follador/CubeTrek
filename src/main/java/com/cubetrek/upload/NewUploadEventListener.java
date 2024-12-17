package com.cubetrek.upload;

import com.cubetrek.database.TrackData;
import com.cubetrek.database.TrackDataRepository;
import com.sunlocator.topolibrary.LatLon;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Component
public class NewUploadEventListener implements ApplicationListener<NewUploadEventListener.OnEvent> {

    Logger logger = LoggerFactory.getLogger(NewUploadEventListener.class);

    @Autowired
    private TrackDataRepository trackDataRepository;

    @Autowired
    private StorageService storageService;

    @Autowired
    private MatchingActivityService matchingActivityService;

    @Async
    @Transactional
    @Override
    public void onApplicationEvent(OnEvent event) {
        this.newUploadFile(event);
    }

    public void newUploadFile(OnEvent event) {
        TrackData newUploadedActivity = trackDataRepository.getReferenceById(event.getTrackId());
        long time = System.currentTimeMillis();
        List<TrackData> mt = trackDataRepository.findMatchingActivities(
                newUploadedActivity.getOwner().getId(),
                newUploadedActivity.getId()
        );
        logger.info("Processed {} matched activities for TrackID '{}' of User ID '{}' in {} ms",
                mt.size(),
                newUploadedActivity.getId(),
                newUploadedActivity.getOwner().getId(),
                (System.currentTimeMillis() - time));

        if (mt.size() >= 2) {
            matchingActivityService.processMatchingActivities(newUploadedActivity, mt);
        }

        String title = determineTitle(event, newUploadedActivity);
        trackDataRepository.updateTrackMetadataTitle(newUploadedActivity.getId(), title);
    }

    private String determineTitle(OnEvent event, TrackData newUploadedActivity) {
        String titleSuggestion = null;
        if (!newUploadedActivity.getTitle().startsWith("Activity on")) {
            titleSuggestion = newUploadedActivity.getTitle();
        }
        return (titleSuggestion != null) ?
                titleSuggestion :
                storageService.createTitleFinal(event.getHighestPoint(), newUploadedActivity, event.getTimezone());
    }


    @Getter
    @Setter
    public static class OnEvent extends ApplicationEvent {
        long trackId;
        LatLon highestPoint;

        String timezone;

        public OnEvent(long trackId, LatLon highestPoint, String timezone) {
            super(trackId);
            this.trackId = trackId;
            this.highestPoint = highestPoint;
            this.timezone = timezone;
        }
    }

}
