package com.cubetrek.upload;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.*;
import com.cubetrek.viewer.ActivitityService;
import com.cubetrek.viewer.TrackViewerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunlocator.topolibrary.GPX.GPXWorker;
import com.sunlocator.topolibrary.HGTFileLoader_LocalStorage;
import com.sunlocator.topolibrary.LatLon;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Date;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IntSummaryStatistics;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;


@Service
public class StorageService {
    Logger logger = LoggerFactory.getLogger(StorageService.class);

    @Autowired
    private TrackDataRepository trackDataRepository;

    @Autowired
    private TrackRawfileRepository trackRawfileRepository;

    @Autowired
    private TrackGeodataRepository trackGeodataRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private GeographyService geographyService;

    @Value("${cubetrek.hgt.1dem}")
    private String hgt_1dem_files;

    @Value("${cubetrek.hgt.3dem}")
    private String hgt_3dem_files;

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy - HH:mm:ss z");

    GeometryFactory gf = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING_SINGLE), 4326);

    HGTFileLoader_LocalStorage hgtFileLoader_3DEM;
    HGTFileLoader_LocalStorage hgtFileLoader_1DEM;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    ActivitityService activitityService;

    @Value("${maptiler.api.key}")
    String maptilerApiKey;

    @PostConstruct
    public void init() {
        //akrobatik needed to use @values in construtor
        hgtFileLoader_3DEM = new HGTFileLoader_LocalStorage(hgt_3dem_files);
        hgtFileLoader_1DEM = new HGTFileLoader_LocalStorage(hgt_1dem_files);
    }

    public UploadResponse store(Users user, MultipartFile file) {
        String filename = file.getOriginalFilename();

        try {
            return store(user, file.getBytes(), filename);
        } catch (IOException e) {
            logger.error("File upload - Failed because IOException 1 - by User "+user.getId(), e);
            throw new ExceptionHandling.FileNotAccepted("File is corrupted");
        }
    }

    final static ZoneId zoneId = ZoneId.of("UTC");

    public UploadResponse store(Users user, byte[] filedata, String filename) {
        Track track = null;
        TrackData trackData = new TrackData();
        TrackGeodata trackgeodata = new TrackGeodata();
        TrackRawfile trackRawfile = new TrackRawfile();

        GPXWorker.ConversionOutput conversionOutput = null;
        boolean isFitFile = true;
        try {
            if (filedata.length>10_000_000) {
                logger.info("File upload - Failed because too large file size - by User "+user.getId()+"'");
                throw new ExceptionHandling.FileNotAccepted("File is too large.");
            }

            if (filename.toLowerCase(Locale.ROOT).endsWith("gzip")||filename.toLowerCase(Locale.ROOT).endsWith("gz")) {
                filename = filename.toLowerCase(Locale.ROOT);
                if (filename.endsWith("gzip"))
                    filename = filename.substring(0, filename.length() - 5);
                else if (filename.endsWith("gz"))
                    filename = filename.substring(0, filename.length() - 3);

                try (ByteArrayInputStream bin = new ByteArrayInputStream(filedata);
                     GZIPInputStream gzipper = new GZIPInputStream(bin))
                {
                    byte[] buffer = new byte[1024];
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    int len;
                    while ((len = gzipper.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                    gzipper.close();
                    out.close();
                    filedata = out.toByteArray();
                }
            }

            if (filename.toLowerCase(Locale.ROOT).endsWith("gpx")) {
                isFitFile = false;
                conversionOutput = GPXWorker.loadGPXTracks(new ByteArrayInputStream(filedata));
            }
            else if (filename.toLowerCase(Locale.ROOT).endsWith("fit"))
                conversionOutput = GPXWorker.loadFitTracks(new ByteArrayInputStream(filedata));
            else {
                logger.info("File upload - Failed because wrong file suffix - by User "+user.getId());
                throw new ExceptionHandling.FileNotAccepted("File type (suffix) not recognized. Either GPX or FIT files accepted.");
            }

            if (conversionOutput.trackList.isEmpty()) {
                logger.error("File upload - Failed because no tracks in Tracklist - by User "+user.getId() + " - Filename: '"+filename+"'; Size: "+(filedata.length/1000)+"kb");
                if (isFitFile)
                    throw new ExceptionHandling.FileNotAccepted("File contains no tracks");
                else
                    throw new ExceptionHandling.FileNotAccepted("File contains no tracks; is this a Route only GPX file?");
            }

            track = conversionOutput.trackList.get(0);

            trackRawfile.setOriginalgpx(filedata);
            trackRawfile.setOriginalfilename(filename);
            trackData.setTrackrawfile(trackRawfile);
        } catch (IOException e) {
            if (e.getMessage().startsWith("FIT fixstatus: 201")) {
                logger.error("File upload - Failed because IOException 2: FIT fixstatus 201 - by User "+user.getId() + " - Filename: '"+filename+"'; Size: "+(filedata.length/1000)+"kb");
                throw new ExceptionHandling.FileNotAccepted("File does not contain GPS data");
            } else {
                logger.error("File upload - Failed because IOException 2 - by User " + user.getId() + " - Filename: '" + filename + "'", e);
                throw new ExceptionHandling.FileNotAccepted("File is corrupted");
            }
        }

        if (track == null || track.isEmpty() || track.getSegments().isEmpty() || track.getSegments().get(0).getPoints().isEmpty() || track.getSegments().get(0).getPoints().size() < 3) {
            logger.info("File upload - Failed because track is empty - by User "+user.getId());
            if (isFitFile)
                throw new ExceptionHandling.FileNotAccepted("File cannot be read or track is empty");
            else
                throw new ExceptionHandling.FileNotAccepted("File cannot be read or track is empty; Perhaps a Route only GPX file?");
        }

        Track reduced = GPXWorker.reduceTrackSegments(track, 2);

        if (reduced.getSegments().get(0).getPoints().size() < 5) {
            logger.info("File upload - Failed because too small - by User "+user.getId());
            throw new ExceptionHandling.FileNotAccepted("GPX file is too small");
        }

        if (reduced.getSegments().get(0).getPoints().size() > 20000) {
            logger.info("File upload - Failed because too many GPX points - by User "+user.getId());
            throw new ExceptionHandling.FileNotAccepted("GPX file is too large");
        }

        trackData.setBBox(GPXWorker.getTrueTrackBoundingBox(reduced));

        //check if timing data is provided
        if (reduced.getSegments().get(0).getPoints().get(0).getTime().isEmpty()) {
            logger.info("File upload - Failed because track does not contain timing data - by User "+user.getId());
            throw new ExceptionHandling.FileNotAccepted("Track does not contain Timing data.");
        }

        boolean allWayPointsHaveElevation = reduced.getSegments().get(0).getPoints().stream()
                .allMatch(wayPoint -> wayPoint.getElevation().isPresent());

        //check if elevation data is provided
        if (!allWayPointsHaveElevation) {
            try {
                reduced = GPXWorker.replaceElevationData(reduced, hgtFileLoader_1DEM, hgtFileLoader_3DEM);
                trackData.setHeightSource(TrackData.Heightsource.CALCULATED);
            } catch (IOException e) {
                logger.error("File upload - Failed because reading Elevation Data IOException - by User "+user.getId(), e);
                throw new ExceptionHandling.FileNotAccepted("Internal server error reading elevation data.");
            }
        } else {
            try {
                reduced = GPXWorker.normalizeElevationData(reduced, hgtFileLoader_1DEM, hgtFileLoader_3DEM);
                trackData.setHeightSource(TrackData.Heightsource.NORMALIZED);
            } catch (IOException e) {
                logger.error("File upload - Failed because reading Elevation Data IOException - by User "+user.getId(), e);
                throw new ExceptionHandling.FileNotAccepted("Internal server error reading elevation data.");
            }
        }

        //Add the reduced track to Trackdata entity

        int segments = reduced.getSegments().size();
        LineString[] lineStrings = new LineString[segments];
        ArrayList<int[]> altitudes = new ArrayList<>();
        ArrayList<ZonedDateTime[]> times = new ArrayList<>();

        LatLon highestPoint = null;
        int highestPointEle = -200;

        try {
            for (int i = 0; i < segments; i++) {
                TrackSegment segment = reduced.getSegments().get(i);
                int points = segment.getPoints().size();
                Coordinate[] cs = new Coordinate[points];
                int[] altitude = new int[points];
                ZonedDateTime[] time = new ZonedDateTime[points];

                for (int j = 0; j < points; j++) {
                    cs[j] = new Coordinate(segment.getPoints().get(j).getLongitude().doubleValue(), segment.getPoints().get(j).getLatitude().doubleValue());
                    altitude[j] = segment.getPoints().get(j).getElevation().get().intValue();
                    time[j] = segment.getPoints().get(j).getTime().get().atZone(zoneId);
                    if (altitude[j] > highestPointEle) {
                        highestPointEle = altitude[j];
                        highestPoint = new LatLon(cs[j].y, cs[j].x);
                    }
                }
                PackedCoordinateSequence.Float fs = new PackedCoordinateSequence.Float(cs, 2);
                lineStrings[i] = new LineString(fs, gf);
                altitudes.add(altitude);
                times.add(time);
            }
        } catch (Exception e) {
            logger.error("File upload - Failed because general exception in conversion to LineString - by User "+user.getId(), e);
            throw new ExceptionHandling.FileNotAccepted("File can't be read or is corrupted.");
        }
        MultiLineString multilinestring = new MultiLineString(lineStrings, gf);
        trackgeodata.setMultiLineString(multilinestring);
        trackgeodata.setAltitudes(altitudes);
        trackgeodata.setTimes(times);

        trackData.setTrackgeodata(trackgeodata);
        trackData.setSharing(user.getSharing());
        trackData.setHeightSource(TrackData.Heightsource.ORIGINAL);

        trackData.setOwner(user);
        trackData.setUploadDate(new Date(System.currentTimeMillis()));
        trackData.setSegments(track.getSegments().size());
        trackData.setDatetrack((track.getSegments().get(0).getPoints().get(0).getTime().orElse(Instant.now())).atZone(zoneId));
        trackData.setTimezone(user.getTimezone());

        GPXWorker.TrackSummary trackSummary = GPXWorker.getTrackSummary(reduced);
        trackData.setElevationup(trackSummary.elevationUp);
        trackData.setElevationdown(trackSummary.elevationDown);
        trackData.setDuration(trackSummary.duration);
        trackData.setDistance(trackSummary.distance);
        trackData.setLowestpoint(trackSummary.lowestpointEle);
        trackData.setHighestpoint(trackSummary.highestpointEle);
        trackData.setComment("");

        trackData.setTitle(createTitlePreliminary(trackData, user.getTimezone()));
        trackData.setActivitytype(getActivitytype(conversionOutput));

        //Check if duplicate
        if (trackDataRepository.existsByOwnerAndDatetrackAndCenterAndDistanceAndDuration(user, trackData.getDatetrack(), trackData.getCenter(), trackData.getDistance(), trackData.getDuration())) {

            TrackData.TrackMetadata trackData_duplicate = trackDataRepository.findMetadataByOwnerAndDatetrackAndCenterAndDistanceAndDuration(user, trackData.getDatetrack(), trackData.getCenter(), trackData.getDistance(), trackData.getDuration()).get(0);
            UploadResponse ur = new UploadResponse();
            ur.setTrackID(trackData_duplicate.getId());
            ur.setTitle(trackData_duplicate.getTitle() + " [Duplicate]");
            ur.setDate(trackData_duplicate.getDatetrack().atZone(ZoneId.of(user.getTimezone())).format(formatter));
            ur.setActivitytype(trackData_duplicate.getActivitytype());
            ur.setTrackSummary(trackSummary);

            logger.info("File upload - Upload of duplicate Track '" + ur.getTrackID() + "' - by User '" + user.getId() + "'");
            return ur;
            //throw new ExceptionHandling.FileNotAccepted("Duplicate: File already exists.");
        }

        trackDataRepository.saveAndFlush(trackData);

        UploadResponse ur = new UploadResponse();
        ur.setTrackID(trackData.getId());
        ur.setTitle(trackData.getTitle());
        ur.setDate(trackData.getDatetrack().atZone(ZoneId.of(user.getTimezone())).format(formatter));
        ur.setActivitytype(trackData.getActivitytype());
        ur.setTrackSummary(trackSummary);
        logger.info("File upload - Successful '" + ur.getTrackID() + "' - by User '" + user.getId() + "'");
        eventPublisher.publishEvent(new NewUploadEventListener.OnEvent(trackData.getId(), highestPoint, user.getTimezone()));
        return ur;
    }

    private TrackData.Activitytype getActivitytype(GPXWorker.ConversionOutput conversionOutput) {
        String sport = conversionOutput.sportString.trim().toLowerCase(Locale.ROOT);
        //See enum 22; https://www.polar.com/accesslink-api/?java#detailed-sport-info-values-in-exercise-entity
        if (sport.isEmpty())
            return TrackData.Activitytype.Unknown;
        if (sport.contains("cross_country_ski"))
            return TrackData.Activitytype.Crosscountryski;
        if (sport.contains("ski"))
            return TrackData.Activitytype.Skimountaineering;
        if (sport.contains("mountaineering"))
            return TrackData.Activitytype.Mountaineering;
        if (sport.contains("running"))
            return TrackData.Activitytype.Run;
        if (sport.contains("hiking"))
            return TrackData.Activitytype.Hike;
        if (sport.contains("cycling"))
            return TrackData.Activitytype.Biking;
        if (sport.contains("snowshoeing"))
            return TrackData.Activitytype.Snowshoeing;
        if (sport.contains("walk"))
            return TrackData.Activitytype.Walking;
        return TrackData.Activitytype.Unknown;
    }

    private String createTitlePreliminary(TrackData trackData, String timeZone) {
        return "Activity on "+ trackData.getDatetrack().atZone(ZoneId.of(timeZone)).format(formatter);
    }

    protected String createTitleFinal(LatLon highestPoint, TrackData trackData, String timeZone) {
        OsmPeaks peak = geographyService.peakWithinRadius(highestPoint, 300);
        if (peak != null) {
            return peak.getName();
        }

        GeographyService.OsmPeakList peak2 = geographyService.findPeaksAlongPath(trackData.getTrackgeodata().getMultiLineString(), 500);
        if (peak2 != null && peak2.getLength()>0) {
            return peak2.getList()[0].getName();
        }

        String reverseGeoCode = reverseGeocode(highestPoint);
        if (reverseGeoCode!=null) {
            return reverseGeoCode;
        }
        logger.info("@@ Reverse Geocode failed... for Track ID "+trackData.getId());
        return createTitlePreliminary(trackData, timeZone);
    }

    private String reverseGeocode(LatLon coord) {
        try {
            String geoFeatureText = "-";
            URL url = new URL(String.format("https://api.maptiler.com/geocoding/%f,%f.json?key=%s", coord.getLongitude(), coord.getLatitude(), maptilerApiKey));
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            InputStream is = connection.getInputStream();

            JsonNode rootNode = (new ObjectMapper()).readTree(is);
            geoFeatureText = rootNode.path("features").get(0).path("text").asText("-");
            if (geoFeatureText.equals("-")) {
                logger.error("@@ Error reverse Geocode, received Json: "+rootNode);
                return null;
            }
            else {
                return geoFeatureText;
            }
        } catch (Exception e) {
            logger.error("@@ Error reverse Geocode for "+coord.toString(), e);
            return null;
        }
    }

    @Transactional
    public UpdateTrackmetadataResponse editTrackmetadata(Authentication authentication, @RequestBody EditTrackmetadataDto editTrackmetadataDto) {
        if (editTrackmetadataDto==null)
            throw new ExceptionHandling.EditTrackmetadataException("Failed to Submit Modifications");

        if (!editTrackmetadataDto.check())
            throw new ExceptionHandling.EditTrackmetadataException(editTrackmetadataDto.getErrorMessage());

        isWriteAccessAllowedToTrack(authentication, editTrackmetadataDto.getIndex());

        trackDataRepository.updateTrackMetadata(editTrackmetadataDto.getIndex(), editTrackmetadataDto.getTitle(), editTrackmetadataDto.getNote(), editTrackmetadataDto.getActivitytype());
        logger.info("Modify ID '"+editTrackmetadataDto.getIndex()+"'");
        return new UpdateTrackmetadataResponse(true);
    }

    @Transactional
    public UpdateTrackmetadataResponse batchRenameMatchingActivities(Authentication authentication, @RequestBody EditTrackmetadataDto editTrackmetadataDto) {
        if (editTrackmetadataDto==null)
            throw new ExceptionHandling.EditTrackmetadataException("Failed to Submit Modifications");

        if (!editTrackmetadataDto.check())
            throw new ExceptionHandling.EditTrackmetadataException(editTrackmetadataDto.getErrorMessage());

        isWriteAccessAllowedToTrackgroup(authentication, editTrackmetadataDto.getIndex());
        Users user = (Users) authentication.getPrincipal();
        trackDataRepository.batchRenameTrackgroup(editTrackmetadataDto.getIndex(), editTrackmetadataDto.getTitle(), user);

        logger.info("Batch rename Trackgroup '"+editTrackmetadataDto.getIndex()+"' by User '"+user.getId()+"'");
        return new UpdateTrackmetadataResponse(true);
    }

    @Transactional
    public UpdateTrackmetadataResponse recalculateHeight(Authentication authentication, long trackId) {

        isWriteAccessAllowedToTrack(authentication, trackId);

        if  (!trackDataRepository.existsById(trackId)) {
            logger.error("Recalculate Height - Failed because reading TrackId does not exist - "+trackId);
            return new UpdateTrackmetadataResponse(false);
        }

        TrackData trackData = trackDataRepository.getReferenceById(trackId);
        TrackGeodata trackGeodata = trackData.getTrackgeodata();

        try {
            ArrayList<short[]> newElevationData = GPXWorker.getElevationDataFromHGT(trackGeodata.getMultiLineString(), hgtFileLoader_1DEM, hgtFileLoader_3DEM);
            trackGeodata.setAltitudes(convertShort2Int(newElevationData));

            int highestpoint = -400000;
            int lowestpoint = 400000;
            int up = 0;
            int down = 0;

            for (short[] elevationData : newElevationData) {
                for (int i=1; i<elevationData.length; i++) {
                    int ele = elevationData[i] - elevationData[i-1];
                    if (ele<0)
                        down -= ele;
                    else
                        up += ele;

                    if (elevationData[i] > highestpoint) {
                        highestpoint = ele;
                    }
                    if (elevationData[i] < lowestpoint) {
                        lowestpoint = ele;
                    }
                }
            }

            trackDataRepository.updateTrackHeightRecalculation(trackId, TrackData.Heightsource.CALCULATED, up, down, highestpoint, lowestpoint);
            trackGeodataRepository.save(trackGeodata);
        } catch (IOException e) {
            logger.error("Recalculate Height - Failed because reading Elevation Data IOException - by TrackId "+trackData.getId(), e);
            return new UpdateTrackmetadataResponse(false);
        }
        logger.info("Recalculate Height of TrackId '"+trackData.getId()+"'");
        return new UpdateTrackmetadataResponse(true);
    }

    private void printList(ArrayList<int[]> elevs) {
        logger.info("Size: "+elevs.size());
        IntSummaryStatistics stats = Arrays.stream(elevs.get(0)).summaryStatistics();
        logger.info(stats.toString());

        for (int i = 0; i < elevs.get(0).length || i < 5; i++) {
            System.out.println(elevs.get(0)[i]);
        }
    }

    private static ArrayList<int[]> convertShort2Int(ArrayList<short[]> shortList) {
        return shortList.stream()
                .map(shortArr ->
                        IntStream.range(0, shortArr.length)
                                .map(i -> shortArr[i])
                                .toArray()
                )
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void isWriteAccessAllowedToTrack(Authentication authentication, long trackId) {
        boolean writeAccess;
        if (authentication instanceof AnonymousAuthenticationToken) //not logged in
            writeAccess = false;
        else {
            Users user = (Users) authentication.getPrincipal();
            long ownerid = trackDataRepository.getOwnerId(trackId);
            writeAccess = ownerid == user.getId();
        }
        if (!writeAccess)
            throw new ExceptionHandling.TrackViewerException(TrackViewerService.noAccessMessage);
    }

    private void isWriteAccessAllowedToTrackgroup(Authentication authentication, long trackgroup) {
        if (authentication instanceof AnonymousAuthenticationToken) //not logged in
            throw new ExceptionHandling.TrackViewerException(TrackViewerService.noAccessMessage);
        else {

            Users user = (Users) authentication.getPrincipal();
            TrackData.TrackMetadata firstExample = activitityService.getMatchingActivities(user, trackgroup).stream().findFirst().orElse(null);
            if (firstExample == null)
                throw new ExceptionHandling.TrackViewerException(TrackViewerService.noAccessMessage);
            else
                isWriteAccessAllowedToTrack(authentication, firstExample.getId());
        }
    }
}