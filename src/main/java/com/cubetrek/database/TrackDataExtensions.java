package com.cubetrek.database;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.*;
import java.util.ArrayList;

@Getter
@Setter
@Entity(name = "trackdataextensions")
@Table(name = "trackdataextensions")
public class TrackDataExtensions implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Lob
    @Convert(converter = HeartrateConverter.class)
    @Column(name = "heartrate")
    private ArrayList<int[]> heartrate;

    @OneToOne(mappedBy = "trackDataExtensions")
    private TrackData trackData;

    @Converter
    public static class HeartrateConverter implements AttributeConverter<ArrayList<int[]>, byte[]> {
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
