package com.cubetrek.upload;

import com.cubetrek.database.*;
import com.cubetrek.ExceptionHandling;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;
import java.sql.Date;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;


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

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy - HH:mm:ss z");

    GeometryFactory gf = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING_SINGLE), 4326);

    HGTFileLoader_LocalStorage hgtFileLoader_3DEM;
    HGTFileLoader_LocalStorage hgtFileLoader_1DEM;

    @PostConstruct
    public void init() {
        //akrobatik needed to use @values in construtor
        hgtFileLoader_3DEM = new HGTFileLoader_LocalStorage(hgt_3dem_files);
        hgtFileLoader_1DEM = new HGTFileLoader_LocalStorage(hgt_1dem_files);
    }

    public UploadResponse store(Users user, MultipartFile file, TimeZone timeZone) {
        //user can be null, will be saved under anonymous user (id = 1)
        Track track = null;
        TrackData trackData = new TrackData();
        TrackGeodata trackgeodata = new TrackGeodata();
        TrackRawfile trackRawfile = new TrackRawfile();

        GPXWorker.ConversionOutput conversionOutput = null;
        try {
            if (file.getSize()>10_000_000) {
                logger.info("File upload - Failed because too large file size - by User "+(user==null? "Anonymous":"'"+user.getId()+"'"));
                throw new ExceptionHandling.FileNotAccepted("File is too large.");
            }
            if (file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith("gpx"))
                conversionOutput = GPXWorker.loadGPXTracks(file.getInputStream());
            else if (file.getOriginalFilename().toLowerCase().endsWith("fit"))
                conversionOutput = GPXWorker.loadFitTracks(file.getInputStream());
            else {
                logger.info("File upload - Failed because wrong file suffix - by User "+(user==null? "Anonymous":"'"+user.getId()+"'"));
                throw new ExceptionHandling.FileNotAccepted("File type (suffix) not recognized. Either GPX or FIT files accepted.");
            }
            track = conversionOutput.trackList.get(0);

            trackRawfile.setOriginalgpx(file.getBytes());
            trackRawfile.setOriginalfilename(file.getOriginalFilename());
            trackData.setTrackrawfile(trackRawfile);
        } catch (IOException e) {
            logger.error("File upload - Failed because IOException - by User "+(user==null? "Anonymous":"'"+user.getId()+"'"), e);
            throw new ExceptionHandling.FileNotAccepted("File is corrupted");
        }

        if (track == null || track.isEmpty() || track.getSegments().isEmpty() || track.getSegments().get(0).getPoints().isEmpty() || track.getSegments().get(0).getPoints().size() < 3) {
            logger.info("File upload - Failed because track is empty - by User "+(user==null? "Anonymous":"'"+user.getId()+"'"));
            throw new ExceptionHandling.FileNotAccepted("File cannot be read or track is empty");
        }

        Track reduced = GPXWorker.reduceTrackSegments(track, 2);

        if (reduced.getSegments().get(0).getPoints().size() < 5) {
            logger.info("File upload - Failed because too small - by User "+(user==null? "Anonymous":"'"+user.getId()+"'"));
            throw new ExceptionHandling.FileNotAccepted("GPX file is too small");
        }

        if (reduced.getSegments().get(0).getPoints().size() > 10000) {
            logger.info("File upload - Failed because too many GPX points - by User "+(user==null? "Anonymous":"'"+user.getId()+"'"));
            throw new ExceptionHandling.FileNotAccepted("GPX file is too large");
        }

        trackData.setBBox(GPXWorker.getTrueTrackBoundingBox(reduced));

        if (trackData.getBBox().getWidthLatMeters() > 100000 || trackData.getBBox().getWidthLonMeters() > 100000) {
            logger.info("File upload - Failed because Area too large - by User "+(user==null? "Anonymous":"'"+user.getId()+"'"));
            throw new ExceptionHandling.FileNotAccepted("Currently only Tracks covering less than 100km x 100km are supported.");
        }

        //check if elevation data is provided
        if (reduced.getSegments().get(0).getPoints().get(0).getTime().isEmpty()) {
            logger.info("File upload - Failed because track does not contain timing data - by User "+(user==null? "Anonymous":"'"+user.getId()+"'"));
            throw new ExceptionHandling.FileNotAccepted("Track does not contain Timing data.");
        }

        //check if elevation data is provided
        if (reduced.getSegments().get(0).getPoints().get(0).getElevation().isEmpty()) {
            try {
                reduced = GPXWorker.replaceElevationData(reduced, hgtFileLoader_1DEM, hgtFileLoader_3DEM);
                trackData.setHeightSource(TrackData.Heightsource.CALCULATED);
            } catch (IOException e) {
                logger.error("File upload - Failed because reading Elevation Data IOException - by User "+(user==null? "Anonymous":"'"+user.getId()+"'"), e);
                throw new ExceptionHandling.FileNotAccepted("Internal server error reading elevation data.");
            }
        } else {
            try {
                reduced = GPXWorker.normalizeElevationData(reduced, hgtFileLoader_1DEM, hgtFileLoader_3DEM);
                trackData.setHeightSource(TrackData.Heightsource.NORMALIZED);
            } catch (IOException e) {
                logger.error("File upload - Failed because reading Elevation Data IOException - by User "+(user==null? "Anonymous":"'"+user.getId()+"'"), e);
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
                    time[j] = segment.getPoints().get(j).getTime().get();
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
            logger.error("File upload - Failed because general exception in converson to LineString - by User "+(user==null? "Anonymous":"'"+user.getId()+"'"), e);
            throw new ExceptionHandling.FileNotAccepted("File can't be read or is corrupted.");
        }
        MultiLineString multilinestring = new MultiLineString(lineStrings, gf);
        trackgeodata.setMultiLineString(multilinestring);
        trackgeodata.setAltitudes(altitudes);
        trackgeodata.setTimes(times);

        trackData.setTrackgeodata(trackgeodata);
        trackData.setSharing(TrackData.Sharing.PUBLIC);
        trackData.setHeightSource(TrackData.Heightsource.ORIGINAL);

        if (user == null) {
            user = usersRepository.getReferenceById(1L);
        }

        trackData.setOwner(user);
        trackData.setUploadDate(new Date(System.currentTimeMillis()));
        trackData.setSegments(track.getSegments().size());
        trackData.setDatetrack(track.getSegments().get(0).getPoints().get(0).getTime().orElse(ZonedDateTime.now()));
        trackData.setTimezone(timeZone.getID());

        GPXWorker.TrackSummary trackSummary = GPXWorker.getTrackSummary(reduced);
        trackData.setElevationUp(trackSummary.elevationUp);
        trackData.setElevationDown(trackSummary.elevationDown);
        trackData.setDuration(trackSummary.duration);
        trackData.setDistance(trackSummary.distance);
        trackData.setLowestpointEle(trackSummary.lowestpointEle);
        trackData.setHighestpointEle(trackSummary.highestpointEle);
        trackData.setComment("");

        trackData.setTitle(createTitle(highestPoint, trackgeodata.getMultiLineString(), trackData, timeZone));
        trackData.setActivitytype(getActivitytype(conversionOutput));

        //Check if duplicate
        if (trackDataRepository.existsByOwnerAndDatetrackAndCenterAndDistanceAndDuration(user, trackData.getDatetrack(), trackData.getCenter(), trackData.getDistance(), trackData.getDuration())) {

            TrackData.TrackMetadata trackData_duplicate = trackDataRepository.findMetadataByOwnerAndDatetrackAndCenterAndDistanceAndDuration(user, trackData.getDatetrack(), trackData.getCenter(), trackData.getDistance(), trackData.getDuration()).get(0);
            UploadResponse ur = new UploadResponse();
            ur.setTrackID(trackData_duplicate.getId());
            ur.setTitle(trackData_duplicate.getTitle() + " [Duplicate]");
            ur.setDate(trackData_duplicate.getDatetrack().toLocalDateTime().atZone(timeZone.toZoneId()).format(formatter));
            ur.setActivitytype(trackData_duplicate.getActivitytype());
            ur.setTrackSummary(trackSummary);

            logger.info("File upload - Upload of duplicate Track '" + ur.getTrackID() + "' - by User '" + user.getId() + "'");
            return ur;
            //throw new ExceptionHandling.FileNotAccepted("Duplicate: File already exists.");
        }

        trackDataRepository.save(trackData);


        UploadResponse ur = new UploadResponse();
        ur.setTrackID(trackData.getId());
        ur.setTitle(trackData.getTitle());
        ur.setDate(trackData.getDatetrack().toLocalDateTime().atZone(timeZone.toZoneId()).format(formatter));
        ur.setActivitytype(trackData.getActivitytype());
        ur.setTrackSummary(trackSummary);
        logger.info("File upload - Successful '" + ur.getTrackID() + "' - by User '" + user.getId() + "'");
        return ur;
    }

    private TrackData.Activitytype getActivitytype(GPXWorker.ConversionOutput conversionOutput) {
        String sport = conversionOutput.sportString.trim().toLowerCase(Locale.ROOT);

        //See enum 22
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
        return TrackData.Activitytype.Unknown;
    }

    private String createTitle(LatLon highestPoint, MultiLineString lineString, TrackData trackData, TimeZone timeZone) {
        OsmPeaks peak = geographyService.peakWithinRadius(highestPoint, 300);
        if (peak != null)
            return peak.getName();

        GeographyService.OsmPeakList peak2 = geographyService.findPeaksAlongPath(lineString, 500);
        if (peak2 != null && peak2.getLength()>0) {
            System.out.println("---- new name: "+peak2.getList()[0].getName());
            return peak2.getList()[0].getName();
        }

        String reverseGeoCode = reverseGeocode(highestPoint);
        if (reverseGeoCode!=null)
            return reverseGeoCode;

        return "Activity on "+ trackData.getDatetrack().toLocalDateTime().atZone(timeZone.toZoneId()).format(formatter);
    }

    private String reverseGeocode(LatLon coord) {
        String geoFeatureText = "-";
        try {
            JsonNode rootNode = (new ObjectMapper()).readTree(new URL(String.format("https://api.maptiler.com/geocoding/%f,%f.json?key=Nq5vDCKAnSrurDLNgtSI", coord.getLongitude(), coord.getLatitude())).openStream());
            geoFeatureText = rootNode.path("features").get(0).path("text").asText("-");
        } catch (Exception e) {
            logger.error("Error reverse Geocode for "+coord.toString(), e);
            return null;
        }
        if (geoFeatureText.equals("-"))
            return null;
        else
            return geoFeatureText;
    }

    @Transactional
    public UpdateTrackmetadataResponse editTrackmetadata(Authentication authentication, @RequestBody EditTrackmetadataDto editTrackmetadataDto) {
        if (editTrackmetadataDto==null)
            throw new ExceptionHandling.EditTrackmetadataException("Failed to Submit Modifications");

        if (!editTrackmetadataDto.check())
            throw new ExceptionHandling.EditTrackmetadataException(editTrackmetadataDto.getErrorMessage());

        isWriteAccessAllowed(authentication, editTrackmetadataDto.getIndex());

        trackDataRepository.updateTrackMetadata(editTrackmetadataDto.getIndex(), editTrackmetadataDto.getTitle(), editTrackmetadataDto.getNote(), editTrackmetadataDto.getActivitytype());
        logger.info("Modify ID '"+editTrackmetadataDto.getIndex()+"'");
        return new UpdateTrackmetadataResponse(true);
    }

    private void isWriteAccessAllowed(Authentication authentication, long trackId) {
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
}