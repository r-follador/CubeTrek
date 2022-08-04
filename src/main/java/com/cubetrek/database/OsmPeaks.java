package com.cubetrek.database;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.sunlocator.topolibrary.LatLon;
import org.hibernate.annotations.Type;
import org.locationtech.jts.geom.Point;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.*;
import java.io.IOException;
import java.io.Serializable;

@Entity(name = "osm_peaks")
@Table(name = "osm_peaks")
@JsonSerialize(using = OsmPeaks.OsmPeaksSerializer.class)
public class OsmPeaks implements Serializable {
    private static final long serialVersionUID = 12L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "geometry")
    @Type(type = "org.locationtech.jts.geom.Point")
    private Point point;

    @Column(name = "name")
    private String name;

    @Column(name = "ele")
    private String ele;

    @Column(name = "ele_calculated")
    private Integer ele_calculated;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Point getPoint() {
        return point;
    }

    public void setPoint(Point point) {
        this.point = point;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEle() {
        return ele;
    }

    public void setEle(String ele) {
        this.ele = ele;
    }

    public void setEle_calculated(int ele_calculated) {
        this.ele_calculated = ele_calculated;
    }

    public int getEle_calculated() {
        return ele_calculated;
    }

    public LatLon getLatLon() {
        return new LatLon(point.getY(), point.getX());
    }

    @Transactional
    protected static class OsmPeaksSerializer extends JsonSerializer<OsmPeaks> {
        @Override
        public void serialize(OsmPeaks value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("name", value.getName());
            gen.writeNumberField("lat", value.getLatLon().getLatitude());
            gen.writeNumberField("lon", value.getLatLon().getLongitude());
            gen.writeEndObject(); // \json
            gen.close();
        }
    }
}
