package com.cubetrek.viewer;

import com.cubetrek.database.TrackMetadata;
import com.cubetrek.database.Users;
import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.TrackMetadataRepository;
import com.sunlocator.topolibrary.GLTFDatafile;
import com.sunlocator.topolibrary.HGTFileLoader_LocalStorage;
import com.sunlocator.topolibrary.HGTWorker;
import com.sunlocator.topolibrary.LatLonBoundingBox;
import com.sunlocator.topolibrary.MapTile.MapTileWorker;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

@Service
public class TrackViewerService {

    @Autowired
    private TrackMetadataRepository trackMetadataRepository;

    HGTFileLoader_LocalStorage hgtFileLoader_3DEM = new HGTFileLoader_LocalStorage("/home/rainer/Software_Dev/HGT/");

    public String getGLTF(Users user, long trackid) {
        TrackMetadata track = trackMetadataRepository.findById(trackid).orElseThrow(() -> new ExceptionHandling.TrackViewerException("Track ID does not exist"));
        isAccessAllowed(user, track);
        LatLonBoundingBox boundingBox = addPadding(track.getBBox());


        GLTFDatafile gltfFile = null;
        try {
            //System.out.println(boundingBox.toString());
            gltfFile = HGTWorker.getTileGLTF_3DEM(boundingBox, calculateZoomlevel(boundingBox), true, hgtFileLoader_3DEM, "map/standard/%d/%d/%d.png");
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new ExceptionHandling.TrackViewerException("IOException: "+e.getMessage());

        }
        return  gltfFile.getString();
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
    public TrackGeojson getTrackGeojson(Users user, long trackid) {
        TrackMetadata track = trackMetadataRepository.findById(trackid).orElseThrow(() -> new ExceptionHandling.TrackViewerException("Track ID does not exist"));
        isAccessAllowed(user, track);

        return new TrackGeojson(track);
    }

    @Transactional
    public String mapView2D(Users user, long trackid, Model model) {
        TrackMetadata track = trackMetadataRepository.findById(trackid).orElseThrow(() -> new ExceptionHandling.TrackViewerException("Track ID does not exist"));
        isAccessAllowed(user, track);

        model.addAttribute("trackmetadata", track);
        model.addAttribute("center_lat", track.getCenter().getCoordinates()[0].y);
        model.addAttribute("center_lon", track.getCenter().getCoordinates()[0].x);
        model.addAttribute("user", user);
        model.addAttribute("writeAccess", true);

        return "trackview_2d";
    }

    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, dd. MMMM yyyy HH:mm");

    @Transactional
    public String mapView3D(Users user, long trackid, Model model) {
        TrackMetadata track = trackMetadataRepository.findById(trackid).orElseThrow(() -> new ExceptionHandling.TrackViewerException("Track ID does not exist"));
        isAccessAllowed(user, track);
        model.addAttribute("trackmetadata", track);
        model.addAttribute("user", user);
        int hours = track.getDuration() / 60;
        int minutes = track.getDuration() % 60;
        model.addAttribute("timeString", String.format("%d:%02d", hours, minutes));
        model.addAttribute("dateCreatedString", track.getDateTrack().format(formatter));
        model.addAttribute("formattedNote", markdownToHTML(track.getComment()));
        return "trackview";
    }

    private final Parser markdownParser =  Parser.builder().build();
    private final HtmlRenderer markdownRenderer = HtmlRenderer.builder().build();
    private String markdownToHTML(String markdown) {
        Node document = markdownParser.parse(markdown);
        return markdownRenderer.render(document);
    }

    private static boolean isAccessAllowed(Users user, TrackMetadata track) {
        if (!track.getOwner().getId().equals(user.getId()) && track.getSharing() != TrackMetadata.Sharing.PUBLIC) {
            throw new ExceptionHandling.TrackViewerException("Track ID cannot be accessed");
        }
        return true;
    }
}
