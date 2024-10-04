package com.cubetrek.database;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
