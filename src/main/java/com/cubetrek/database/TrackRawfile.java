package com.cubetrek.database;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Entity(name = "trackrawfile")
@Table(name = "trackrawfile")
public class TrackRawfile {
    @Getter
    @Setter
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Lob
    @Column(name = "originalgpx")
    private byte[] originalgpx;

    public byte[] getOriginalgpx() {
        return gzipUncompress(originalgpx);
    }

    public void setOriginalgpx(byte[] originalgpx) {
        this.originalgpx = gzipCompress(originalgpx);
    }

    @Getter
    @Setter
    @Column(name = "originalfilename")
    private String originalfilename;

    @Getter
    @Setter
    @OneToOne(mappedBy = "trackrawfile")
    private TrackData trackData;


    private static byte[] gzipCompress(byte[] uncompressedData) {
        byte[] result = new byte[]{};
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(uncompressedData.length);
             GZIPOutputStream gzipOS = new GZIPOutputStream(bos)) {
            gzipOS.write(uncompressedData);
            // You need to close it before using bos
            gzipOS.close();
            result = bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private static byte[] gzipUncompress(byte[] compressedData) {
        byte[] result = new byte[]{};
        try (ByteArrayInputStream bis = new ByteArrayInputStream(compressedData);
             ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPInputStream gzipIS = new GZIPInputStream(bis)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIS.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            result = bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }
}
