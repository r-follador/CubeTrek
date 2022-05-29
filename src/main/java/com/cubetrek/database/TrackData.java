package com.cubetrek.database;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.locationtech.jts.geom.MultiLineString;
import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;

@Getter
@Setter
@Entity(name = "trackdata")
@Table(name = "trackdata")
public class TrackData implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "multilinestring")
    @Type(type = "org.locationtech.jts.geom.MultiLineString")
    private MultiLineString multiLineString;

    @Lob
    @Column(name = "times")
    private ArrayList<ZonedDateTime[]> times;

    @Lob
    @Column(name = "altitudes")
    private ArrayList<int[]> altitudes;

    @OneToOne(mappedBy = "trackdata")
    private TrackMetadata trackMetadata;
}
