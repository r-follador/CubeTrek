package com.cubetrek.upload;

import com.cubetrek.database.TrackData;
import com.cubetrek.database.TrackDataRepository;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sound.midi.Track;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


@Component
public class NewUploadEventListener implements ApplicationListener<OnNewUploadEvent> {

    Logger logger = LoggerFactory.getLogger(NewUploadEventListener.class);

    @Autowired
    private TrackDataRepository trackDataRepository;

    @Autowired
    private StorageService storageService;

    @Async
    @Transactional
    @Override
    public void onApplicationEvent(OnNewUploadEvent event) {
        this.newUploadFile(event);
    }

    public void newUploadFile(OnNewUploadEvent event) {

        TrackData newUploadedActivity = trackDataRepository.getReferenceById(event.getTrackId());

        //Get the final title of the activity
        String title = storageService.createTitleFinal(event.getHighestPoint(), newUploadedActivity, event.getTimezone());
        newUploadedActivity.setTitle(title);
        trackDataRepository.save(newUploadedActivity);


        ///Find matching activities of the same owner
        long time = System.currentTimeMillis();
        List<TrackData> mt = trackDataRepository.findMatchingActivities(newUploadedActivity.getOwner().getId(), newUploadedActivity.getId());

        if (mt.size() >= 2) {

            List<TrackData> unassignedActivities = new ArrayList<>();
            Long groupid = null;
            for (TrackData t : mt) {
                if (t.getTrackgroup()!= null &&  t.getTrackgroup() != 0) {
                    if (groupid== null) {
                        groupid = t.getTrackgroup();
                    } else {
                        if (t.getTrackgroup()!=groupid)
                            unassignedActivities.add(t);
                    }
                } else {
                    unassignedActivities.add(t);
                }
            }

            if (groupid==null)
                groupid = new Random().nextLong();

            for (TrackData nt : unassignedActivities)
                nt.setTrackgroup(groupid);

            trackDataRepository.saveAll(unassignedActivities);
            logger.info("Found "+mt.size()+" matched activities for TrackID '"+newUploadedActivity.getId()+"' of User ID: '"+newUploadedActivity.getOwner().getId()+"' in "+(System.currentTimeMillis()-time)+"ms");
        }
        //


    }

}
