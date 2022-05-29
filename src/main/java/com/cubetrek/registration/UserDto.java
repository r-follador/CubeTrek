package com.cubetrek.registration;

import lombok.Getter;
import lombok.Setter;

/**
 * Convinience class to transfer essential user data during registration
 */

public class UserDto {
    @Getter
    @Setter
    private String name;
    @Getter
    @Setter
    private String password;
    @Getter
    @Setter
    private String matchingPassword;
    @Getter
    @Setter
    private String email;
}