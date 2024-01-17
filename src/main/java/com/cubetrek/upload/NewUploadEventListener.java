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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


@Component
public class NewUploadEventListener implements ApplicationListener<NewUploadEventListener.OnEvent> {

    Logger logger = LoggerFactory.getLogger(NewUploadEventListener.class);

    @Autowired
    private TrackDataRepository trackDataRepository;

    @Autowired
    private StorageService storageService;

    @Async
    @Transactional
    @Override
    public void onApplicationEvent(OnEvent event) {
        this.newUploadFile(event);
    }

    public void newUploadFile(OnEvent event) {
        TrackData newUploadedActivity = trackDataRepository.getReferenceById(event.getTrackId());
        ///Find matching activities of the same owner
        long time = System.currentTimeMillis();
        List<TrackData> mt = trackDataRepository.findMatchingActivities(newUploadedActivity.getOwner().getId(), newUploadedActivity.getId());

        String titleSuggestion = null;

        if (mt.size() >= 2) {
            List<TrackData> unassignedActivities = new ArrayList<>();
            Long groupid = null;
            for (TrackData t : mt) {
                if (t.getTrackgroup()!= null &&  t.getTrackgroup() != 0) {
                    if (groupid== null) {
                        groupid = t.getTrackgroup();
                    } else {
                        if (!t.getTrackgroup().equals(groupid))
                            unassignedActivities.add(t);
                    }
                } else {
                    unassignedActivities.add(t);
                }
            }

            if (!mt.get(0).getTitle().startsWith("Activity on"))
                titleSuggestion = mt.get(0).getTitle();

            if (groupid==null)
                groupid = new Random().nextLong();

            for (TrackData nt : unassignedActivities)
                nt.setTrackgroup(groupid);

            trackDataRepository.saveAllAndFlush(unassignedActivities);
            logger.info("Found "+mt.size()+" matched activities for TrackID '"+newUploadedActivity.getId()+"' of User ID: '"+newUploadedActivity.getOwner().getId()+"' in "+(System.currentTimeMillis()-time)+"ms");
        }

        //Get the final title of the activity
        if (titleSuggestion!= null) {
            trackDataRepository.updateTrackMetadataTitle(newUploadedActivity.getId(), titleSuggestion);
        } else {
            String title = storageService.createTitleFinal(event.getHighestPoint(), newUploadedActivity, event.getTimezone());
            trackDataRepository.updateTrackMetadataTitle(newUploadedActivity.getId(), title);
        }
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
