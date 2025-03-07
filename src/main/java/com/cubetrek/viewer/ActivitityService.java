package com.cubetrek.viewer;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.TrackData;
import com.cubetrek.database.TrackDataRepository;
import com.cubetrek.database.Users;
import com.cubetrek.database.UsersExtensions;
import jakarta.transaction.Transactional;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.yaml.snakeyaml.nodes.Tag.STR;

@Service
public class ActivitityService {

    @Autowired
    TrackDataRepository trackDataRepository;

    public String getActivityHeatmapAsJSON(Users user, String timeZone) {
        String out = trackDataRepository.getDailyAggregatedStatsAsJSON(user.getId(), timeZone);
        if (out == null || out.isBlank())
            return "[]";
        return out;
    }

    public String getActivityCumulativeAsJSON(Users user, String timeZone) {
        String out = trackDataRepository.getDailyStatsAsJSON(user.getId(), timeZone);
        if (out == null || out.isBlank())
            return "[]";
        return out;
    }

    public String getMonthlyTotalAsJSON(Users user, String timeZone) {
        String out = trackDataRepository.getMonthlyAggregatedStatsAsJSON(user.getId(), timeZone);
        if (out == null || out.isBlank())
            return "[]";
        return out;
    }

    public String getYearlyTotalAsJSON(Users user, String timeZone) {
        String out = trackDataRepository.getYearlyAggregatedStatsAsJSON(user.getId(), timeZone);
        if (out == null || out.isBlank())
            return "[]";
        return out;
    }

    @Transactional
    public String getHeartrateZonesAsJSON(Users user) {
        int maxHeartrate = user.getUsersExtensions().flatMap(UsersExtensions::getMaximumHeartRate).orElse(180);
        return String.format("""
                [
                    {"zoneName": "Zone 1",
                    "zoneThreshold": %d},
                    {"zoneName": "Zone 2",
                    "zoneThreshold": %d},
                    {"zoneName": "Zone 3",
                    "zoneThreshold": %d},
                    {"zoneName": "Zone 4",
                    "zoneThreshold": %d},
                    {"zoneName": "Zone 5",
                    "zoneThreshold": %d}
                ]
                """,
                Math.round(maxHeartrate*0.6),
                Math.round(maxHeartrate*0.7),
                Math.round(maxHeartrate*0.8),
                Math.round(maxHeartrate*0.9),
                maxHeartrate);

    }

    public List<ActivityCount> getActivityTypeCount(Users user) {
        //convert List of ActivityCountInterface to list of ActivityCount
        return trackDataRepository.getActivityCounts(user.getId()).stream()
                .map(ActivityCount::new)
                .collect(Collectors.toList());
    }

    public List<TrackData.TrekmapperData> getAllTracksPosition(Users user) {
        //get latest activity for each trackgroup (and all remaining activities for non-grouped activities)
        return trackDataRepository.findAllTracksPositionByUser(user.getId());
    }

    public static class ActivityCount {
        public TrackData.Activitytype activitytype;
        public int count;
        public ActivityCount(TrackDataRepository.ActivityCountInterface act) {
            activitytype = TrackData.Activitytype.values()[act.getActivitytype()];
            count = act.getCount();
        }
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


    public List<TrackData.TrackMetadata> getTenRecentActivities(Users user) {
        return trackDataRepository.findMostRecent(user.getId(), 10);
    }

    public List<TrackData.TrackMetadata> getFavoriteActivities(Users user) {
        return trackDataRepository.findFavorite(user.getId());
    }

    public int getActivityCount(Users user) {
        return trackDataRepository.countTotalActivities(user.getId());
    }

    public List<TrackData.TrackMetadata> getActivityOfDay(Users user, int year, int month, int day, String timezone) {
        month++; //convert from javascript/java numbering starting with 0=jan to SQL 1=jan
        //Sanity check
        if (year > 2100 || year < 1900 || day < 1 || day > 31 || month < 1 || month > 12)
            throw new ExceptionHandling.UnnamedExceptionJson("Date out of range");
        String dateDay = year+"-"+String.format("%02d", month)+"-"+String.format("%02d", day);
        System.out.println("@@@@@ Dateday "+dateDay);
        return trackDataRepository.findTrackOfGivenDay(user.getId(), dateDay, timezone);
    }

    public List<TrackDataRepository.MatchedActivityInterface> getMatchedActivityOverview(Users user) {
        return trackDataRepository.getMatchedActivities(user.getId(), 20);
    }

    public List<TrackData.TrackMetadata> getMatchingActivities(Users user, Long matchgroup) {
        return trackDataRepository.findByOwnerAndTrackgroupAndHiddenOrderByDatetrackDesc(user, matchgroup, false);
    }


    public List<TrackData.TrackMetadata> getActivitiesList(Users user, Integer size, Integer pageNo, String sort, boolean descending, TrackData.Activitytype activitytype) {
        Sort sorter;
        sorter = descending ? Sort.by(sort).descending() : Sort.by(sort).ascending();
        PageRequest paging = PageRequest.of(pageNo, size, sorter);
        if (activitytype == null)
            return trackDataRepository.findByOwnerAndHidden(user, false, paging).stream().toList();
        else
            return trackDataRepository.findByOwnerAndHiddenAndActivitytype(user, false, activitytype, paging);
    }

    public List<TrackData.TrackMetadata> getHiddenList(Users user) {
        return trackDataRepository.findByOwnerAndHidden(user, true);
    }

    public List<TrackData.PublicActivity> getPublicActivitiesList(int size) {
        return trackDataRepository.findPublicActivities(size);
    }

    public long countNumberOfEntries(Users user) {
        return trackDataRepository.countByOwnerAndHidden(user, false);
    }
}
