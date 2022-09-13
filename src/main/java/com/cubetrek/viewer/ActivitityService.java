package com.cubetrek.viewer;

import com.cubetrek.database.TrackData;
import com.cubetrek.database.TrackDataRepository;
import com.cubetrek.database.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.awt.print.Pageable;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TimeZone;

@Service
public class ActivitityService {

    @Autowired
    TrackDataRepository trackDataRepository;

    public String getActivityHeatmapAsJSON(Users user, TimeZone timeZone) {
        return trackDataRepository.getAggregatedStatsAsJSON(user.getId(), timeZone.getID());
    }
    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, dd. MMMM yyyy HH:mm");
    public TopActivities getTopActivities(Users user) {
        TopActivities out = new TopActivities();
        out.recentDistance = trackDataRepository.findTopDistanceLast3Month(user.getId(), 5);
        out.alltimeDistance = trackDataRepository.findTopDistanceAlltime(user.getId(), 5);
        out.recentAscent = trackDataRepository.findTopAscentLast3Month(user.getId(), 5);
        out.alltimeAscent = trackDataRepository.findTopAscentAlltime(user.getId(), 5);
        out.recentPeak = trackDataRepository.findTopPeakLast3Month(user.getId(), 5);
        out.alltimePeak = trackDataRepository.findTopPeakAlltime(user.getId(), 5);
        return out;
    }

    public static class TopActivities {
        public List<TrackData.TrackMetadata> recentDistance;
        public List<TrackData.TrackMetadata> alltimeDistance;
        public List<TrackData.TrackMetadata> recentAscent;
        public List<TrackData.TrackMetadata> alltimeAscent;
        public List<TrackData.TrackMetadata> recentPeak;
        public List<TrackData.TrackMetadata> alltimePeak;
    }


    public Page<TrackData.TrackMetadata> getPaginatedList(Users user, Integer pageNo) {
        PageRequest paging = PageRequest.of(pageNo, 10, Sort.by("datetrack").descending());
        return trackDataRepository.findByOwnerAndHidden(user, false, paging);
    }


}
