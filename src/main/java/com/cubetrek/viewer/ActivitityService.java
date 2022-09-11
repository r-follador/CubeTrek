package com.cubetrek.viewer;

import com.cubetrek.database.TrackData;
import com.cubetrek.database.TrackDataRepository;
import com.cubetrek.database.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ActivitityService {

    @Autowired
    TrackDataRepository trackDataRepository;

    public String getActivityHeatmapAsJSON(Users user) {
        return trackDataRepository.getAggregatedStatsAsJSON(user.getId());
    }
    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, dd. MMMM yyyy HH:mm");
    public List<TrackData.TrackMetadata> getTopActivities(Users user) {
        List<TrackData.TrackMetadata> out = trackDataRepository.findTopDistanceLast3Month(user.getId(), 5);
        for (TrackData.TrackMetadata t : out) {
            System.out.println(t.getDatetrack());
        }
        return out;

    }


}
