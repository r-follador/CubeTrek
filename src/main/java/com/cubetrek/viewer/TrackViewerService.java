package com.cubetrek.viewer;

import com.cubetrek.database.*;
import com.cubetrek.ExceptionHandling;
import com.sunlocator.topolibrary.*;
import com.sunlocator.topolibrary.GPX.GPXWorker;
import com.sunlocator.topolibrary.MapTile.GLTFWorker;
import com.sunlocator.topolibrary.MapTile.MapTileWorker;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.TimeZone;

@Service
public class TrackViewerService {

    @Autowired
    private TrackDataRepository trackDataRepository;

    @Autowired
    private TrackRawfileRepository trackRawfileRepository;

    @Value("${cubetrek.hgt.1dem}")
    private String hgt_1dem_files;

    @Value("${cubetrek.hgt.3dem}")
    private String hgt_3dem_files;

    HGTFileLoader_LocalStorage hgtFileLoader_3DEM;
    GeometryFactory gf = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING_SINGLE), 4326);

    @PostConstruct
    public void init() {
        //akrobatik needed to use @values in constructor
        hgtFileLoader_3DEM= new HGTFileLoader_LocalStorage( hgt_3dem_files);
    }

    final static public String noAccessMessage = "Track ID does not exist or access denied";

    Logger logger = LoggerFactory.getLogger(TrackViewerService.class);

    public String getGLTF(Authentication authentication, long trackid) {
        TrackData track = trackDataRepository.findById(trackid).orElseThrow(() -> new ExceptionHandling.TrackViewerException(noAccessMessage));
        if (!isReadAccessAllowed(authentication, track))
            throw new ExceptionHandling.TrackViewerException(noAccessMessage);

        LatLonBoundingBox boundingBox = addPadding(track.getBBox());
        String output;
        try {
            output = getGLTFString_cacheable(trackid, boundingBox);
        } catch (IOException e) {
            logger.error("",e);
            e.printStackTrace(System.err);
            throw new ExceptionHandling.TrackViewerException("IOException: "+e.getMessage());
        }
        return output;
    }

    //make this function cacheable end using the id as key
    @Cacheable(cacheNames = "gltf", key = "#id")
    public String getGLTFString_cacheable(long id, LatLonBoundingBox boundingBox) throws IOException {
        GLTFDatafile gltfDatafile = new GLTFWorker.GLTFBuilder(boundingBox, hgtFileLoader_3DEM).setZoomlevel(calculateZoomlevel(boundingBox)).setEnclosement(true).setTextureUrl("map/standard/%d/%d/%d.png").build();
        return gltfDatafile.getString();
    }

    public static int calculateZoomlevel(LatLonBoundingBox boundingBox) {
        int zoom = 14;
        while (MapTileWorker.calculateRequiredTilesFromBoundingBox(boundingBox, zoom) > 48) {
            zoom--;
        }
        return zoom;
    }

    public static LatLonBoundingBox addPadding(LatLonBoundingBox boundingBox) {
        return boundingBox.addPadding(500);
    }

    @Transactional
    public TrackGeojson getTrackGeojson(Authentication authentication, long trackid) {
        TrackData track = trackDataRepository.findById(trackid).orElseThrow(() -> new ExceptionHandling.TrackViewerException(noAccessMessage));
        if (!isReadAccessAllowed(authentication, track))
            throw new ExceptionHandling.TrackViewerException(noAccessMessage);

        return new TrackGeojson(track);
    }

    /**
     * get a slim geojson (only lat/lon coordinates, no altitude, no timing, no distance); only title and id as properties
     * @param authentication to check for read privileges
     * @param trackid
     * @param reduceTrackEpsilon if >0 will further reduce the number of points by the given epsilon (in meters)
     * @return
     */
    @Transactional
    public SlimTrackGeojson getSlimTrackGeojson(Authentication authentication, long trackid, double reduceTrackEpsilon) {
        TrackData trackdata = trackDataRepository.findById(trackid).orElseThrow(() -> new ExceptionHandling.TrackViewerException(noAccessMessage));
        if (!isReadAccessAllowed(authentication, trackdata))
            throw new ExceptionHandling.TrackViewerException(noAccessMessage);

        if (reduceTrackEpsilon <= 0) {
            return new SlimTrackGeojson(trackdata);
        }

        Track trackReduced = GPXWorker.reduceTrackSegments(trackdata.getTrackgeodata().getMultiLineString(), reduceTrackEpsilon);

        LineString[] lineStrings = new LineString[trackReduced.getSegments().size()];

        for (int i = 0; i < trackReduced.getSegments().size(); i++) {
            TrackSegment segment = trackReduced.getSegments().get(i);
            int points = segment.getPoints().size();
            Coordinate[] cs = new Coordinate[points];

            for (int j = 0; j < points; j++) {
                cs[j] = new Coordinate(segment.getPoints().get(j).getLongitude().doubleValue(), segment.getPoints().get(j).getLatitude().doubleValue());
            }
            PackedCoordinateSequence.Float fs = new PackedCoordinateSequence.Float(cs, 2);
            lineStrings[i] = new LineString(fs, gf);
        }

        MultiLineString multilinestring = new MultiLineString(lineStrings, gf);

        TrackData out = new TrackData();
        out.setTitle(trackdata.getTitle());
        out.setId(trackdata.getId());
        out.setDatetrack(trackdata.getDatetrack());
        out.setTrackgroup(trackdata.getTrackgroup());
        out.setActivitytype(trackdata.getActivitytype());
        TrackGeodata tgd = new TrackGeodata();
        tgd.setMultiLineString(multilinestring);
        out.setTrackgeodata(tgd);
        return new SlimTrackGeojson(out);
    }

    public String getEncodedPolyline(Authentication authentication, long trackid, double reduceTrackEpsilon) {
        TrackData trackdata = trackDataRepository.findById(trackid).orElseThrow(() -> new ExceptionHandling.TrackViewerException(noAccessMessage));
        if (!isReadAccessAllowed(authentication, trackdata))
            throw new ExceptionHandling.TrackViewerException(noAccessMessage);

        Track trackReduced = GPXWorker.reduceTrackSegments(trackdata.getTrackgeodata().getMultiLineString(), reduceTrackEpsilon);

        return GPXWorker.encode2EPA(Collections.max(trackReduced.getSegments(), Comparator.comparingInt(segment -> segment.getPoints().size())));
    }

    public String getEncodedPolylineSecret(long trackid, double reduceTrackEpsilon) {
        TrackData trackdata = trackDataRepository.findById(trackid).orElseThrow(() -> new ExceptionHandling.TrackViewerException(noAccessMessage));

        Track trackReduced = GPXWorker.reduceTrackSegments(trackdata.getTrackgeodata().getMultiLineString(), reduceTrackEpsilon);

        return GPXWorker.encode2EPA(Collections.max(trackReduced.getSegments(), Comparator.comparingInt(segment -> segment.getPoints().size())));
    }

    final DateTimeFormatter formatter_datetime = DateTimeFormatter.ofPattern("EEEE, dd. MMMM yyyy HH:mm z");
    final DateTimeFormatter formatter_date = DateTimeFormatter.ofPattern("EEEE, dd. MMMM yyyy");

    @Transactional(readOnly  = true)
    public String mapView3D(Authentication authentication, long trackid, Model model) {
        logger.info("View Track ID '"+trackid+"' by " + (authentication instanceof AnonymousAuthenticationToken?"Anonymous":("User ID '"+((Users) authentication.getPrincipal()).getId()+"'")));
        TrackData track = trackDataRepository.findById(trackid).orElseThrow(() -> new ExceptionHandling.TrackAccessException(noAccessMessage));
        if (!isReadAccessAllowed(authentication, track))
            throw new ExceptionHandling.TrackAccessException(noAccessMessage);

        model.addAttribute("trackmetadata", track);
        int hours = track.getDuration() / 60;
        int minutes = track.getDuration() % 60;
        model.addAttribute("timeString", String.format("%d:%02d", hours, minutes));
        model.addAttribute("datetimeCreatedString", track.getDatetrack().atZone(TimeZone.getDefault().toZoneId()).format(formatter_datetime));
        model.addAttribute("dateCreatedString", track.getDatetrack().atZone(TimeZone.getDefault().toZoneId()).format(formatter_date));
        model.addAttribute("formattedNote", markdownToHTML(track.getComment()));
        model.addAttribute("writeAccess", isWriteAccessAllowed(authentication, track));
        model.addAttribute("owner", track.getOwner().getName());
        model.addAttribute("ownerIsSupporter", track.getOwner().getUserTier()== Users.UserTier.PAID);
        model.addAttribute("isLoggedIn", !(authentication instanceof AnonymousAuthenticationToken));

        //check if too big for 3D view (more than 100x100km)
        if (track.getBBox().getWidthLatMeters() > 100000 || track.getBBox().getWidthLonMeters() > 100000) {
            model.addAttribute("tracktoobig", true);
            return "trackview2d";
        }
        return "trackview";
    }

    @Transactional(readOnly  = true)
    public String mapView3dReplay(Authentication authentication, long trackid, Model model) {
        logger.info("View Track-3dReplay ID '"+trackid+"' by " + (authentication instanceof AnonymousAuthenticationToken?"Anonymous":("User ID '"+((Users) authentication.getPrincipal()).getId()+"'")));
        TrackData track = trackDataRepository.findById(trackid).orElseThrow(() -> new ExceptionHandling.TrackAccessException(noAccessMessage));
        if (!isReadAccessAllowed(authentication, track))
            throw new ExceptionHandling.TrackAccessException(noAccessMessage);

        model.addAttribute("trackmetadata", track);
        int hours = track.getDuration() / 60;
        int minutes = track.getDuration() % 60;
        model.addAttribute("timeString", String.format("%d:%02d", hours, minutes));
        model.addAttribute("datetimeCreatedString", track.getDatetrack().atZone(TimeZone.getDefault().toZoneId()).format(formatter_datetime));
        model.addAttribute("dateCreatedString", track.getDatetrack().atZone(TimeZone.getDefault().toZoneId()).format(formatter_date));
        model.addAttribute("formattedNote", markdownToHTML(track.getComment()));
        model.addAttribute("writeAccess", isWriteAccessAllowed(authentication, track));
        model.addAttribute("owner", track.getOwner().getName());
        model.addAttribute("ownerIsSupporter", track.getOwner().getUserTier()== Users.UserTier.PAID);
        model.addAttribute("isLoggedIn", !(authentication instanceof AnonymousAuthenticationToken));

        //check if too big for 3D view (more than 100x100km)
        if (track.getBBox().getWidthLatMeters() > 100000 || track.getBBox().getWidthLonMeters() > 100000) {
            model.addAttribute("tracktoobig", true);
            return "trackview2d";
        }
        return "trackview3dreplay";
    }


    @Transactional(readOnly  = true)
    public String mapView2D(Authentication authentication, long trackid, Model model) {
        logger.info("View Track ID '"+trackid+"' by " + (authentication instanceof AnonymousAuthenticationToken?"Anonymous":("User ID '"+((Users) authentication.getPrincipal()).getId()+"'")));
        TrackData track = trackDataRepository.findById(trackid).orElseThrow(() -> new ExceptionHandling.TrackAccessException(noAccessMessage));
        if (!isReadAccessAllowed(authentication, track))
            throw new ExceptionHandling.TrackAccessException(noAccessMessage);

        model.addAttribute("trackmetadata", track);
        int hours = track.getDuration() / 60;
        int minutes = track.getDuration() % 60;
        model.addAttribute("timeString", String.format("%d:%02d", hours, minutes));
        model.addAttribute("datetimeCreatedString", track.getDatetrack().atZone(TimeZone.getDefault().toZoneId()).format(formatter_datetime));
        model.addAttribute("dateCreatedString", track.getDatetrack().atZone(TimeZone.getDefault().toZoneId()).format(formatter_date));
        model.addAttribute("formattedNote", markdownToHTML(track.getComment()));
        model.addAttribute("writeAccess", isWriteAccessAllowed(authentication, track));
        model.addAttribute("owner", track.getOwner().getName());
        model.addAttribute("ownerIsSupporter", track.getOwner().getUserTier()== Users.UserTier.PAID);
        model.addAttribute("isLoggedIn", !(authentication instanceof AnonymousAuthenticationToken));
        return "trackview2d";
    }

    private final Parser markdownParser =  Parser.builder().build();
    private final HtmlRenderer markdownRenderer = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build();
    private String markdownToHTML(String markdown) {
        if (markdown == null)
            return "";
        Node document = markdownParser.parse(markdown);
        return markdownRenderer.render(document);
    }

    private boolean isReadAccessAllowed(Authentication authentication, TrackData track) {
        //throw new ExceptionHandling.TrackViewerException("Track ID cannot be accessed");
        if (track.getSharing() == TrackData.Sharing.PUBLIC) //open for anyone
            return true;
        else if (authentication instanceof AnonymousAuthenticationToken) //not logged in
            return false;
        else {
            Users user = (Users) authentication.getPrincipal();
            return track.getOwner().getId().equals(user.getId());
        }
    }

    private boolean isWriteAccessAllowed(Authentication authentication, TrackData track) {
        if (authentication instanceof AnonymousAuthenticationToken) //not logged in
            return false;
        else {
            Users user = (Users) authentication.getPrincipal();
            long ownerid = track.getOwner().getId();
            return ownerid == user.getId();
        }
     }
     @Transactional
    public byte[] downloadTrackfile(Authentication authentication, long trackid, HttpServletResponse response) {
        logger.info("Download Track ID '"+trackid+"' by " + (authentication instanceof AnonymousAuthenticationToken?"Anonymous":("User ID '"+((Users) authentication.getPrincipal()).getId()+"'")));
        TrackData track = trackDataRepository.findById(trackid).orElseThrow(() -> new ExceptionHandling.TrackAccessException("Not Found"));
        Users user = (Users) authentication.getPrincipal();
        if (!track.getOwner().getId().equals(user.getId()))
            throw new ExceptionHandling.TrackAccessException("Not Found");

        long trackRawfileId = track.getTrackrawfile().getId();
        TrackRawfile trackRawfile = trackRawfileRepository.getReferenceById(trackRawfileId);
        response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="+trackRawfile.getOriginalfilename());

        return trackRawfile.getOriginalgpx();
    }
}
