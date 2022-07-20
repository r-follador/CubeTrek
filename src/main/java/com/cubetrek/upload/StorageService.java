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


@Service
public class StorageService {
    Logger logger = LoggerFactory.getLogger(StorageService.class);

    @Autowired
    private TrackMetadataRepository trackMetadataRepository;

    @Autowired
    private TrackRawfileRepository trackRawfileRepository;

    @Autowired
    private TrackDataRepository trackDataRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private GeographyService geographyService;

    @Value("${cubetrek.hgt.1dem}")
    private String hgt_1dem_files;

    @Value("${cubetrek.hgt.3dem}")
    private String hgt_3dem_files;

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy - HH:mm:ss");

    GeometryFactory gf = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING_SINGLE), 4326);

    HGTFileLoader_LocalStorage hgtFileLoader_3DEM;
    HGTFileLoader_LocalStorage hgtFileLoader_1DEM;

    @PostConstruct
    public void init() {
        //akrobatik needed to use @values in construtor
        hgtFileLoader_3DEM = new HGTFileLoader_LocalStorage(hgt_3dem_files);
        hgtFileLoader_1DEM = new HGTFileLoader_LocalStorage(hgt_1dem_files);
    }

    public UploadResponse store(Users user, MultipartFile file) {
        //user can be null, will be saved under anonymous user (id = 1)
        Track track = null;
        TrackMetadata trackMetadata = new TrackMetadata();
        TrackData trackdata = new TrackData();
        TrackRawfile trackRawfile = new TrackRawfile();

        GPXWorker.ConversionOutput conversionOutput = null;
        try {
            if (file.getSize()>5_000_000)
                throw new ExceptionHandling.FileNotAccepted("File is too large.");
            if (file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith("gpx"))
                conversionOutput = GPXWorker.loadGPXTracks(file.getInputStream());
            else if (file.getOriginalFilename().toLowerCase().endsWith("fit"))
                conversionOutput = GPXWorker.loadFitTracks(file.getInputStream());
            else
                throw new ExceptionHandling.FileNotAccepted("File type (suffix) not recognized. Either GPX or FIT files accepted.");
            track = conversionOutput.trackList.get(0);

            trackRawfile.setOriginalgpx(file.getBytes());
            trackRawfile.setOriginalfilename(file.getOriginalFilename());
            trackMetadata.setTrackrawfile(trackRawfile);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ExceptionHandling.FileNotAccepted("File is corrupted");
        }

        if (track == null || track.isEmpty() || track.getSegments().isEmpty() || track.getSegments().get(0).getPoints().isEmpty() || track.getSegments().get(0).getPoints().size() < 3) {
            throw new ExceptionHandling.FileNotAccepted("File cannot be read or track is empty");
        }


        Track reduced = GPXWorker.reduceTrackSegments(track, 2);

        if (reduced.getSegments().get(0).getPoints().size() < 5) {
            throw new ExceptionHandling.FileNotAccepted("GPX file is too small");
        }

        if (reduced.getSegments().get(0).getPoints().size() > 10000) {
            throw new ExceptionHandling.FileNotAccepted("GPX file is too large");
        }


        trackMetadata.setBBox(GPXWorker.getTrueTrackBoundingBox(reduced));


        if (trackMetadata.getBBox().getWidthLatMeters() > 100000 || trackMetadata.getBBox().getWidthLonMeters() > 100000) {
            throw new ExceptionHandling.FileNotAccepted("Currently only Tracks covering less than 100km x 100km are supported.");
        }

        //check if elevation data is provided
        if (reduced.getSegments().get(0).getPoints().get(0).getTime().isEmpty()) {
            throw new ExceptionHandling.FileNotAccepted("Track does not contain Timing data.");
        }

        //check if elevation data is provided
        if (reduced.getSegments().get(0).getPoints().get(0).getElevation().isEmpty()) {
            try {
                reduced = GPXWorker.replaceElevationData(reduced, hgtFileLoader_1DEM, hgtFileLoader_3DEM);
                trackMetadata.setHeightSource(TrackMetadata.Heightsource.CALCULATED);
            } catch (IOException e) {
                e.printStackTrace();
                throw new ExceptionHandling.FileNotAccepted("Internal server error reading elevation data.");
            }
        } else {
            try {
                reduced = GPXWorker.normalizeElevationData(reduced, hgtFileLoader_1DEM, hgtFileLoader_3DEM);
                trackMetadata.setHeightSource(TrackMetadata.Heightsource.NORMALIZED);
            } catch (IOException e) {
                e.printStackTrace();
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
            throw new ExceptionHandling.FileNotAccepted("File can't be read or is corrupted.");
        }
        MultiLineString multilinestring = new MultiLineString(lineStrings, gf);
        trackdata.setMultiLineString(multilinestring);
        trackdata.setAltitudes(altitudes);
        trackdata.setTimes(times);

        trackMetadata.setTrackdata(trackdata);
        trackMetadata.setSharing(TrackMetadata.Sharing.PUBLIC);
        trackMetadata.setHeightSource(TrackMetadata.Heightsource.ORIGINAL);

        if (user == null) {
            user = usersRepository.getReferenceById(1L);
        }

        trackMetadata.setOwner(user);
        trackMetadata.setUploadDate(new Date(System.currentTimeMillis()));
        trackMetadata.setSegments(track.getSegments().size());
        trackMetadata.setDateTrack(track.getSegments().get(0).getPoints().get(0).getTime().orElse(ZonedDateTime.now()));
        trackMetadata.setTimezone(trackMetadata.getDateTrack().getZone().getId());

        GPXWorker.TrackSummary trackSummary = GPXWorker.getTrackSummary(reduced);
        trackMetadata.setElevationUp(trackSummary.elevationUp);
        trackMetadata.setElevationDown(trackSummary.elevationDown);
        trackMetadata.setDuration(trackSummary.duration);
        trackMetadata.setDistance(trackSummary.distance);
        trackMetadata.setLowestpointEle(trackSummary.lowestpointEle);
        trackMetadata.setHighestpointEle(trackSummary.highestpointEle);
        trackMetadata.setComment("");

        trackMetadata.setTitle(createTitle(highestPoint, trackMetadata));
        trackMetadata.setActivitytype(getActivitytype(conversionOutput));

        //Check if duplicate
        if (trackMetadataRepository.existsByOwnerAndDateTrackAndCenterAndDistanceAndDuration(user, trackMetadata.getDateTrack(), trackMetadata.getCenter(), trackMetadata.getDistance(), trackMetadata.getDuration())) {

            TrackMetadata trackMetadata_duplicate = trackMetadataRepository.findByOwnerAndDateTrackAndCenterAndDistanceAndDuration(user, trackMetadata.getDateTrack(), trackMetadata.getCenter(), trackMetadata.getDistance(), trackMetadata.getDuration()).get(0);
            UploadResponse ur = new UploadResponse();
            ur.setTrackID(trackMetadata_duplicate.getId());
            ur.setTitle(trackMetadata_duplicate.getTitle() + " [Duplicate]");
            ur.setDate(trackMetadata_duplicate.getDateTrack());
            ur.setActivitytype(trackMetadata_duplicate.getActivitytype());
            ur.setTrackSummary(trackSummary);

            return ur;
            //throw new ExceptionHandling.FileNotAccepted("Duplicate: File already exists.");
        }

        trackMetadataRepository.save(trackMetadata);


        UploadResponse ur = new UploadResponse();
        ur.setTrackID(trackMetadata.getId());
        ur.setTitle(trackMetadata.getTitle());
        ur.setDate(trackMetadata.getDateTrack());
        ur.setActivitytype(trackMetadata.getActivitytype());
        ur.setTrackSummary(trackSummary);

        return ur;
    }

    private TrackMetadata.Activitytype getActivitytype(GPXWorker.ConversionOutput conversionOutput) {
        String sport = conversionOutput.sportString.trim().toLowerCase(Locale.ROOT);

        //See enum 22
        if (sport.isEmpty())
            return TrackMetadata.Activitytype.Unknown;
        if (sport.contains("cross_country_ski"))
            return TrackMetadata.Activitytype.Crosscountryski;
        if (sport.contains("ski"))
            return TrackMetadata.Activitytype.Skimountaineering;
        if (sport.contains("mountaineering"))
            return TrackMetadata.Activitytype.Mountaineering;
        if (sport.contains("running"))
            return TrackMetadata.Activitytype.Run;
        if (sport.contains("hiking"))
            return TrackMetadata.Activitytype.Hike;
        if (sport.contains("cycling"))
            return TrackMetadata.Activitytype.Biking;
        if (sport.contains("snowshoeing"))
            return TrackMetadata.Activitytype.Snowshoeing;
        return TrackMetadata.Activitytype.Unknown;
    }

    private String createTitle(LatLon highestPoint, TrackMetadata trackMetadata) {
        OsmPeaks peak = geographyService.peakWithinRadius(highestPoint, 300);
        if (peak != null)
            return peak.getName();

        String reverseGeoCode = reverseGeocode(highestPoint);
        if (reverseGeoCode!=null)
            return reverseGeoCode;

        return "Activity on "+ trackMetadata.getDateTrack().format(formatter);
    }

    private String reverseGeocode(LatLon coord) {
        String geoFeatureText = "-";
        try {
            JsonNode rootNode = (new ObjectMapper()).readTree(new URL(String.format("https://api.maptiler.com/geocoding/%f,%f.json?key=Nq5vDCKAnSrurDLNgtSI", coord.getLongitude(), coord.getLatitude())).openStream());
            geoFeatureText = rootNode.path("features").get(0).path("text").asText("-");
        } catch (Exception e) {
            logger.error("Error reverse Geocode", e);
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

        trackMetadataRepository.updateTrackMetadata(editTrackmetadataDto.getIndex(), editTrackmetadataDto.getTitle(), editTrackmetadataDto.getNote(), editTrackmetadataDto.getActivitytype());
        logger.info("Modify ID '"+editTrackmetadataDto.getIndex()+"'");
        return new UpdateTrackmetadataResponse(true);
    }

    private void isWriteAccessAllowed(Authentication authentication, long trackId) {
        boolean writeAccess;
        if (authentication instanceof AnonymousAuthenticationToken) //not logged in
            writeAccess = false;
        else {
            Users user = (Users) authentication.getPrincipal();
            long ownerid = trackMetadataRepository.getOwnerId(trackId);
            writeAccess = ownerid == user.getId();
        }
        if (!writeAccess)
            throw new ExceptionHandling.TrackViewerException(TrackViewerService.noAccessMessage);
    }
}