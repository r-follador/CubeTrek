package com.cubetrek.database;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity(name = "trackrawfile")
@Table(name = "trackrawfile")
public class TrackRawfile {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Lob
    @Column(name = "originalgpx")
    private byte[] originalgpx;

    @Column(name = "originalfilename")
    private String originalfilename;

    @OneToOne(mappedBy = "trackrawfile")
    private TrackData trackData;

}
