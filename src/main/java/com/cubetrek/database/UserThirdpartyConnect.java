package com.cubetrek.database;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
public class UserThirdpartyConnect {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne(targetEntity = Users.class, fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "user_id")
    private Users user;

    @Column
    private String garminUseraccesstoken;

    @Column
    private String garminUseraccesstokenSecret;

    @Column
    private boolean garminEnabled = false;

    @Column
    private String polarUseraccesstoken;

    @Column
    private String polarUserid;

    @Column
    private boolean polarEnabled = false;

    @Column(columnDefinition = "TEXT")
    private String suuntoUseraccesstoken;

    @Column(columnDefinition = "TEXT")
    private String suuntoRefreshtoken;

    @Column
    private String suuntoUserid;

    @Column
    private boolean suuntoEnabled = false;



}
