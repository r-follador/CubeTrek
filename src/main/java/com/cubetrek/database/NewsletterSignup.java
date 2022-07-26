package com.cubetrek.database;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.sql.Date;

@Getter
@Setter
@Entity(name = "newslettersignup")
@Table(name = "newslettersignup")
public class NewsletterSignup {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "email")
    private String email;

    @Column(name = "date")
    private Date date;
}
