package com.cubetrek.upload;

import com.cubetrek.database.TrackData;
import com.cubetrek.database.TrackDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Component
public class MatchingActivityService {
    @Autowired
    private TrackDataRepository trackDataRepository;

    Logger logger = LoggerFactory.getLogger(MatchingActivityService.class);

    public void processMatchingActivities(TrackData newUploadedActivity, List<TrackData> mt) {
        List<TrackData> unassignedActivities = new ArrayList<>();
        Long groupId = null;

        for (TrackData t : mt) {
            if (t.getTrackgroup() != null && t.getTrackgroup() != 0) {
                if (groupId == null) {
                    groupId = t.getTrackgroup();
                } else if (!t.getTrackgroup().equals(groupId)) {
                    unassignedActivities.add(t);
                }
            } else {
                unassignedActivities.add(t);
            }
        }

        if (groupId == null) {
            groupId = new Random().nextLong();
        }

        List<Long> idsToUpdate = unassignedActivities.stream().map(TrackData::getId).toList();

        trackDataRepository.updateTrackgroupForIds(groupId, idsToUpdate);
        // Save unassigned activities
    }
}
