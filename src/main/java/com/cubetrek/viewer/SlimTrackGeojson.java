package com.cubetrek.viewer;

import com.cubetrek.database.TrackData;
import com.cubetrek.database.TrackGeodata;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.sunlocator.topolibrary.HGTWorker;
import com.sunlocator.topolibrary.LatLon;
import com.sunlocator.topolibrary.LatLonBoundingBox;
import com.sunlocator.topolibrary.MapTile.MapTile;
import com.sunlocator.topolibrary.MapTile.MapTileWorker;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;

@JsonSerialize(using = SlimTrackGeojson.GeojsonSerializer.class)
public class SlimTrackGeojson implements Serializable{

    @Serial
    private static final long serialVersionUID = 2135L;

    TrackData metadata;
    TrackGeodata data;


    public SlimTrackGeojson(TrackData metadata) {
        this.metadata = metadata;
        data =metadata.getTrackgeodata();
    }

    @Transactional
    protected static class GeojsonSerializer extends JsonSerializer<SlimTrackGeojson> {
        @Override
        public void serialize(SlimTrackGeojson value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("type", "Feature");

            gen.writeFieldName("geometry");
            gen.writeStartObject();

                gen.writeStringField("type", "MultiLineString");
                int trackSize = value.data.getMultiLineString().getNumGeometries();
                gen.writeFieldName("coordinates");
                gen.writeStartArray();
                for (int i=0; i<trackSize; i++) { //Loop over Segments
                    gen.writeStartArray();
                    Coordinate[] cs = value.data.getMultiLineString().getGeometryN(i).getCoordinates();
                    int points = value.data.getMultiLineString().getGeometryN(i).getNumPoints();

                    for (int j=0; j<points; j++) { //Loop over Points
                        if (j>0) {
                            LatLon currentPoint = new LatLon(cs[j].getY(), cs[j].getX());
                        }
                        gen.writeStartArray();
                            gen.writeNumber((float)cs[j].x); //lon
                            gen.writeNumber((float)cs[j].y); //lat
                        gen.writeEndArray();
                    }
                    gen.writeEndArray();
                }
                gen.writeEndArray();

            gen.writeEndObject(); // \geometries

            if (value.metadata != null) {
                gen.writeFieldName("properties");
                gen.writeStartObject();
                if (value.metadata.getTitle()!=null)
                    gen.writeStringField("name", value.metadata.getTitle());
                if (value.metadata.getId()!=null)
                    gen.writeNumberField("id", value.metadata.getId());
                if (value.metadata.getTrackgroup() != null)
                    gen.writeStringField("trackgroup", value.metadata.getTrackgroup().toString());
                gen.writeEndObject(); // \properties
            }
            gen.writeEndObject(); // \json
            gen.close();
        }
    }

    public static class TileBbox implements Serializable {
        @Serial
        private static final long serialVersionUID = 299L;

        @Getter
        @Setter
        double N_Bound;

        @Getter
        @Setter
        double S_Bound;

        @Getter
        @Setter
        double W_Bound;

        @Getter
        @Setter
        double E_Bound;

        @Getter
        @Setter
        double widthLatDegree;

        @Getter
        @Setter
        double widthLonDegree;

        @Getter
        @Setter
        double widthLatMeters;

        @Getter
        @Setter
        double widthLonMeters;

        @Getter
        @Setter
        int tile_zoom;

        @Getter
        @Setter
        int tile_x;

        @Getter
        @Setter
        int tile_y;


        public TileBbox(LatLonBoundingBox bbox, int zoom, int x, int y) {
            this.E_Bound = bbox.getE_Bound();
            this.N_Bound = bbox.getN_Bound();
            this.W_Bound = bbox.getW_Bound();
            this.S_Bound = bbox.getS_Bound();
            this.widthLatDegree = bbox.getWidthLatDegree();
            this.widthLonDegree = bbox.getWidthLonDegree();
            this.widthLatMeters = bbox.getWidthLatMeters();
            this.widthLonMeters = bbox.getWidthLonMeters();
            this.tile_zoom = zoom;
            this.tile_x = x;
            this.tile_y = y;
        }
    }

    private ArrayList<TileBbox> getTileBBoxes() {
        ArrayList<TileBbox> tileBBox = new ArrayList<>();
        LatLonBoundingBox boundingBox = TrackViewerService.addPadding(new LatLonBoundingBox(metadata.getBbox_N(), metadata.getBbox_S(), metadata.getBbox_W(), metadata.getBbox_E()));

        MapTile[][] mtiles = MapTileWorker.getTilesFromBoundingBox(boundingBox, TrackViewerService.calculateZoomlevel(boundingBox));
        int width = mtiles.length;
        int height = mtiles[0].length;

        for (int x=0; x<width; x++) {
            for (int y = 0; y < height; y++) {
                tileBBox.add(new TileBbox(mtiles[x][y].getBoundingBox(), mtiles[x][y].zoom, mtiles[x][y].x, mtiles[x][y].y));
            }
        }
        return tileBBox;
    }
}
