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


}