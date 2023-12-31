package com.cubetrek.database;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.sunlocator.topolibrary.LatLonBoundingBox;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.springframework.web.util.HtmlUtils;

import javax.persistence.*;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;

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
        Kite("Kiting", "cubetrek_icon_kite.svg"),
        Walking("Walking", "cubetrek_icon_walk.svg");

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

    @Getter
    @Setter
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Getter
    @Setter
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "trackgeodata_id", referencedColumnName = "id") //creates a foreign key column called trackdata_id
    private TrackGeodata trackgeodata;

    @Getter
    @Setter
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "trackrawfile_id", referencedColumnName = "id") //creates a foreign key column called trackrawfile_id
    private TrackRawfile trackrawfile;

    @Getter
    @Setter
    @ManyToOne(cascade= CascadeType.MERGE, fetch = FetchType.EAGER)
    @JoinColumn(name = "owner")
    private Users owner;

    @Getter
    @Setter
    @Column(name = "title")
    private String title;

    @Getter
    @Setter
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "sharing")
    private Sharing sharing;

    @Getter
    @Setter
    @Column(name = "upload_date")
    private Date uploadDate;

    @Getter
    @Setter
    @Column(name = "center")
    @Type(type = "org.locationtech.jts.geom.Point")
    private Point center;

    @Getter
    @Setter
    @Column(name = "bbox_N")
    private Float bbox_N;

    @Getter
    @Setter
    @Column(name = "bbox_S")
    private Float bbox_S;

    @Getter
    @Setter
    @Column(name = "bbox_W")
    private Float bbox_W;

    @Getter
    @Setter
    @Column(name = "bbox_E")
    private Float bbox_E;

    @Getter
    @Setter
    @Column(name = "elevationup")
    private Integer elevationup;

    @Getter
    @Setter
    @Column(name = "elevationdown")
    private Integer elevationdown;

    @Getter
    @Setter
    @Column(name = "distance")
    private Integer distance;

    @Getter
    @Setter
    @Column(name = "highestpoint")
    private Integer highestpoint;

    @Getter
    @Setter
    @Column(name = "lowestpoint")
    private Integer lowestpoint;

    @Getter
    @Column(name = "datetrack", columnDefinition= "TIMESTAMPTZ")
    private java.sql.Timestamp datetrack; //datetrack normalized to UTC

    public void setDatetrack(ZonedDateTime datetrack) {
        this.datetrack = Timestamp.valueOf(datetrack.toLocalDateTime());
    }

    public void setDatetrack(java.sql.Timestamp datetrack) {
        this.datetrack = datetrack;
    }

    @Getter
    @Setter
    @Column(name = "duration")
    private Integer duration;

    @Getter
    @Setter
    @Column(name = "source")
    private String source;

    @Getter
    @Setter
    @Column(name = "segments")
    private Integer segments;

    @Getter
    @Setter
    @Enumerated
    @Column(name = "height_source")
    private Heightsource heightSource;

    @Getter
    @Setter
    @Enumerated
    @Column(name = "activitytype")
    private Activitytype activitytype;

    @Getter
    @Setter
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Getter
    @Setter
    @Column(name = "timezone")
    private String timezone; //TimeZone of uploader

    @Getter
    @Setter
    @Column(name = "hidden", columnDefinition = "boolean default false")
    private boolean hidden;

    @Getter
    @Setter
    @Column(name = "favorite", columnDefinition = "boolean default false")
    private boolean favorite;

    @Getter
    @Setter
    @Column(name = "trackgroup")
    private Long trackgroup;

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
                "elevationUp=" + elevationup + '\'' +
                "elevationDown=" + elevationdown + '\'' +
                "datetrack=" + datetrack + '\'' +
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

        @JsonSerialize(using = TitleSerializer.class, as=String.class)
        String getTitle();
        TrackData.Sharing getSharing();
        Integer getElevationup();
        Integer getElevationdown();
        Integer getDistance();
        Integer getHighestpoint();
        Integer getLowestpoint();

        @JsonSerialize(using = TimestampSerializer.class)
        java.sql.Timestamp getDatetrack();
        Integer getDuration();
        TrackData.Activitytype getActivitytype();
        boolean isHidden();
        boolean isFavorite();

        @JsonSerialize(using= ToStringSerializer.class)
        Long getTrackgroup();
    }

    public interface TrekmapperData {
        Long getId();

        @JsonSerialize(using = TitleSerializer.class, as=String.class)
        String getTitle();

        @JsonSerialize(using = TimestampSerializer.class)
        java.sql.Timestamp getDatetrack();

        double getLatitude();

        double getLongitude();

        TrackData.Activitytype getActivitytype();

        boolean isFavorite();

        @JsonSerialize(using= ToStringSerializer.class)
        Long getTrackgroup();

        int getTrackgroupentrycount();
    }

    public static class TimestampSerializer extends JsonSerializer<Timestamp> {
        @Override
        public void serialize(Timestamp value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            gen.writeString(isoDateFormat.format(value));
        }
    }

    public static class TitleSerializer extends JsonSerializer<String> {
        @Override
        public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeObject(HtmlUtils.htmlEscape(value));
        }
    }
}
