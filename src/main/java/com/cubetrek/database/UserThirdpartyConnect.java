package com.cubetrek.database;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;

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

    @Convert(converter = DataEncryptDecryptConverter.class)
    @Column
    private String garminUseraccesstokenSecret; //to be encrypted/decrypted

    @Column(unique = true)
    private String garminUserid;

    @Convert(converter = DataEncryptDecryptConverter.class)
    @Column(columnDefinition = "TEXT")
    private String garminOauth2AccessToken;

    @Convert(converter = DataEncryptDecryptConverter.class)
    @Column(columnDefinition = "TEXT")
    private String garminOauth2RefreshToken;

    @Column
    private Instant garminOauth2AccessTokenExpiresAt;

    @Column
    private Instant garminOauth2RefreshTokenExpiresAt;

    @Column
    private Instant garminOauth2ExchangeStartedAt;

    @Column
    private Instant garminOauth2MigrationCompletedAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean garminOauth2ReconnectRequired = false;

    @Column
    private boolean garminEnabled = false;

    @Convert(converter = DataEncryptDecryptConverter.class)
    @Column
    private String polarUseraccesstoken; //to be encrypted/decrypted

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

    @Column
    private boolean corosEnabled = false;

    @Column
    String corosUserid;

    @Convert(converter = DataEncryptDecryptConverter.class)
    @Column
    String corosAccessToken;

    @Column
    String corosRefreshToken;

    @Column
    Integer corosExpiresIn;


    @Component
    @Converter
    public static class DataEncryptDecryptConverter implements AttributeConverter<String, String> {

        @Autowired
        AESEncryptionService aesEncryptionService;

        @Override
        public String convertToDatabaseColumn(String attribute) {
            if (attribute == null || attribute.isEmpty())
                return "";
            try {
                return aesEncryptionService.encrypt(attribute);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isEmpty())
                return "";
            try {
                return aesEncryptionService.decrypt(dbData);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
