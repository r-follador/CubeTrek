package com.cubetrek.database;

import com.sunlocator.topolibrary.LatLonBoundingBox;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Date;
import java.time.ZonedDateTime;

@Getter
@Setter
@Entity(name = "trackdata")
@Table(name = "trackdata")
public class TrackData implements Serializable {

    public enum Sharing {
        PRIVATE, FRIENDS, PUBLIC;
    }

    public enum Heightsource {
        ORIGINAL, CALCULATED, MODIFIED, NORMALIZED;
    }

    public enum Activitytype {
        Unknown("Unknown", "cubetrek_icon_unknownactivity.svg"),
        Hike("Hiking", "cubetrek_icon_hiking.svg"),
        Run("Running", "cubetrek_icon_running.svg"),
        Skimountaineering("Ski Mountaineering", "cubetrek_icon_skitouring.svg"),
        Crosscountryski ("Cross-Country Skiing", "cubetrek_icon_xcountry_skiing.svg"),
        Mountaineering("Mountaineering", "cubetrek_icon_mountaineering.svg"),
        Biking("Biking", "cubetrek_icon_mountainbike.svg"),
        Snowshoeing("Snowshoeing", "cubetrek_icon_snowshoe.svg"),
        Downhillskiing("Downhill Skiing", "cubetrek_icon_downhill_skiing.svg"),
        Ebike("E-Biking", "cubetrek_icon_ebike.svg"),
        Watersports("Watersports", "cubetrek_icon_canoe.svg"),
        Kite("Kiting", "cubetrek_icon_kite.svg");

        private final String displayValue;
        private final String iconName;

        private Activitytype(String displayValue, String iconName) {
            this.displayValue = displayValue;
            this.iconName = iconName;
        }

        public String getDisplayValue() {
            return displayValue;
        }

        public String getIconName() {
            return iconName;
        }
    }


    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "trackdata_id", referencedColumnName = "id") //creates a foreign key column called trackdata_id
    private TrackGeodata trackdata;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "trackrawfile_id", referencedColumnName = "id") //creates a foreign key column called trackrawfile_id
    private TrackRawfile trackrawfile;

    @ManyToOne(cascade= CascadeType.MERGE)
    @JoinColumn(name = "owner")
    private Users owner;

    @Column(name = "title")
    private String title;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "sharing")
    private Sharing sharing;

    @Column(name = "upload_date")
    private Date uploadDate;

    @Column(name = "center")
    @Type(type = "org.locationtech.jts.geom.Point")
    private Point center;

    @Column(name = "bbox_N")
    private Float bbox_N;

    @Column(name = "bbox_S")
    private Float bbox_S;

    @Column(name = "bbox_W")
    private Float bbox_W;

    @Column(name = "bbox_E")
    private Float bbox_E;

    @Column(name = "elevation_up")
    private Integer elevationUp;

    @Column(name = "elevation_down")
    private Integer elevationDown;

    @Column(name = "distance")
    private Integer distance;

    @Column(name = "highestpoint")
    private Integer highestpointEle;

    @Column(name = "lowestpoint")
    private Integer lowestpointEle;

    @Column(name = "date_track")
    private ZonedDateTime dateTrack;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "source")
    private String source;

    @Column(name = "segments")
    private Integer segments;

    @Enumerated
    @Column(name = "height_source")
    private Heightsource heightSource;

    @Enumerated
    @Column(name = "activitytype")
    private Activitytype activitytype;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "timezone")
    private String timezone;

    @Column(name = "hidden", columnDefinition = "boolean default false")
    private boolean hidden;

    @Column(name = "favorite", columnDefinition = "boolean default false")
    private boolean favorite;

    public void setBBox(LatLonBoundingBox bbox) {
        setBbox_N((float)bbox.getN_Bound());
        setBbox_E((float)bbox.getE_Bound());
        setBbox_S((float)bbox.getS_Bound());
        setBbox_W((float)bbox.getW_Bound());

        GeometryFactory gf = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING_SINGLE), 4326);
        Coordinate[] cs = {new Coordinate(bbox.getCenter().getLongitude(), bbox.getCenter().getLatitude())};
        PackedCoordinateSequence.Float fs = new PackedCoordinateSequence.Float(cs, 2);
        Point centerpoint = new Point(fs, gf);

        setCenter(centerpoint);
    }

    public LatLonBoundingBox getBBox() {
        return new LatLonBoundingBox(getBbox_N(), getBbox_S(), getBbox_W(), getBbox_E());
    }

    @Override
    public String toString() {
        return "Tracks{" +
                "id=" + id + '\'' +
                "owner=" + owner + '\'' +
                "title=" + title + '\'' +
                "sharing=" + sharing + '\'' +
                "uploadDate=" + uploadDate + '\'' +
                "N/E/S/W=" + bbox_N +", "+ bbox_E +", "+ bbox_S +", "+ bbox_W +", " + '\'' +
                "center=" + center.toString() + '\'' +
                "elevationUp=" + elevationUp + '\'' +
                "elevationDown=" + elevationDown + '\'' +
                "dateTrack=" + dateTrack + '\'' +
                "duration=" + duration + '\'' +
                "source=" + source + '\'' +
                "heightSource=" + heightSource + '\'' +
                "activitytype=" + activitytype + '\'' +
                "comment=" + comment + '\'' +
                "timezone=" + timezone + '\'' +
                '}';
    }

    public interface TrackMetadata {
        Long getId();
        Users getOwner();
        String getTitle();
        TrackData.Sharing getSharing();
        Integer getElevationUp();
        Integer getElevationDown();
        Integer getDistance();
        Integer getHighestpointEle();
        Integer getLowestpointEle();
        java.time.ZonedDateTime getDateTrack();
        Integer getDuration();
        TrackData.Activitytype getActivitytype();
        boolean isHidden();
        boolean isFavorite();
    }
}
