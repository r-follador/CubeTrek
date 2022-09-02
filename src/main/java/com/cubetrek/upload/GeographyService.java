package com.cubetrek.upload;

import com.cubetrek.database.OsmPeaks;
import com.cubetrek.database.OsmPeaksRepository;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.sunlocator.topolibrary.GPX.GPXWorker;
import com.sunlocator.topolibrary.LatLon;
import com.sunlocator.topolibrary.LatLonBoundingBox;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class GeographyService {
    @Autowired
    private OsmPeaksRepository osmPeaksRepository;

    private final GeometryFactory gf = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING_SINGLE), 4326);

    public Point convertLatLon2Point(LatLon latLon) {
        Coordinate[] coordinate = {new Coordinate(latLon.getLongitude(), latLon.getLatitude())};
        PackedCoordinateSequence.Float fs = new PackedCoordinateSequence.Float(coordinate, 2);
        return new Point(fs, gf);
    }

    public List<OsmPeaks> findClosestPeaks(LatLon point, int limitResutls) {
        return osmPeaksRepository.findNearestPeaks(convertLatLon2Point(point), limitResutls);
    }

    /**
     *
     * @param point center
     * @param radius in meter
     * @return closest peak within the radius, null if none found
     */
    public OsmPeaks peakWithinRadius(LatLon point, int radius) {
        List<OsmPeaks> peaks = findClosestPeaks(point, 1);
        if (peaks.size()<1)
            return null;
        if (point.calculateEuclidianDistance(peaks.get(0).getLatLon())<=radius)
            return peaks.get(0);
        return null;
    }

    public OsmPeakList findPeaksWithinBBox(LatLonBoundingBox bbox) {
        OsmPeakList out = new OsmPeakList();
        out.setList(osmPeaksRepository.findPeaksWithinBBox(bbox.getW_Bound(), bbox.getS_Bound(), bbox.getE_Bound(), bbox.getN_Bound()).toArray(OsmPeaks[]::new));
        return out;
    }

    public OsmPeakList findPeaksAlongPath(MultiLineString lineString, int maxDistanceMeters) {
        OsmPeakList out = new OsmPeakList();
        out.setList(osmPeaksRepository.findPeaksAroundLine((LineString) lineString.getGeometryN(0), (double) maxDistanceMeters).toArray(OsmPeaks[]::new));
        return out;
    }


    @JsonSerialize(using = OsmPeakList.OsmPeakListSerializer.class)
    public static class OsmPeakList {
        @Getter
        @Setter
        OsmPeaks[] list;

        public int getLength() {
            return list.length;
        }


        public static class OsmPeakListSerializer extends JsonSerializer<OsmPeakList> {
            @Override
            public void serialize(OsmPeakList value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeStartObject();
                gen.writeFieldName("peaklist");
                gen.writeStartArray();
                for (OsmPeaks peaks : value.getList()) {
                    gen.writeStartObject();
                    gen.writeStringField("name", peaks.getName());
                    gen.writeNumberField("lat", peaks.getLatLon().getLatitude());
                    gen.writeNumberField("lon", peaks.getLatLon().getLongitude());
                    gen.writeEndObject();
                }
                gen.writeEndArray();
                gen.writeEndObject(); // \json
                gen.close();
            }
        }
    }

}
