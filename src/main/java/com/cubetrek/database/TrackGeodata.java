package com.cubetrek.database;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.MultiLineString;

import java.io.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;

@Getter
@Setter
@Entity(name = "trackgeodata")
@Table(name = "trackgeodata")
public class TrackGeodata implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "multilinestring")
    private MultiLineString multiLineString;

    @Lob
    @Convert(converter = TimesTypeConverter.class)
    @Column(name = "times")
    private ArrayList<ZonedDateTime[]> times;

    @Lob
    @Convert(converter = AltitudeTypeConverter.class)
    @Column(name = "altitudes")
    private ArrayList<int[]> altitudes;

    @OneToOne(mappedBy = "trackgeodata")
    private TrackData trackData;


    @Converter
    public static class TimesTypeConverter implements AttributeConverter<ArrayList<ZonedDateTime[]>, byte[]> {
        @Override
        public byte[] convertToDatabaseColumn(ArrayList<ZonedDateTime[]> attribute) {
            if (attribute == null) {
                return null;
            }
            return serializeObject(attribute);
        }

        @Override
        public ArrayList<ZonedDateTime[]> convertToEntityAttribute(byte[] dbData) {
            if (dbData == null) {
                return null;
            }
            return (ArrayList<ZonedDateTime[]>) deserializeObject(dbData);
        }
    }

    @Converter
    public static class AltitudeTypeConverter implements AttributeConverter<ArrayList<int[]>, byte[]> {
        @Override
        public byte[] convertToDatabaseColumn(ArrayList<int[]> attribute) {
            if (attribute == null) {
                return null;
            }
            return serializeObject(attribute);
        }

        @Override
        public ArrayList<int[]> convertToEntityAttribute(byte[] dbData) {
            if (dbData == null) {
                return null;
            }
            return (ArrayList<int[]>) deserializeObject(dbData);
        }
    }


    private static byte[] serializeObject(Object obj) {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream();
             ObjectOutputStream o = new ObjectOutputStream(b)) {
            o.writeObject(obj);
            return b.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Serialization error", e);
        }
    }

    // Utility method for deserialization
    private static Object deserializeObject(byte[] bytes) {
        try (ByteArrayInputStream b = new ByteArrayInputStream(bytes);
             ObjectInputStream o = new ObjectInputStream(b)) {
            return o.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Deserialization error", e);
        }
    }
}
