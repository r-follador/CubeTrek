package com.cubetrek.registration;

import com.cubetrek.database.TrackData;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Convenience class to transfer essential user data during registration
 */

public class UserDto {
    @Getter
    @Setter
    @NotNull
    @NotBlank(message = "Name cannot be empty")
    @Size(min=1, max=255, message = "Name must be between 1 and 255 characters")
    private String name;

    @Getter
    @Setter
    @NotNull
    @NotBlank(message = "Password cannot be empty")
    @Size(min=5, message = "Password must be at least 5 characters")
    private String password;

    @Getter
    @Setter
    @NotNull
    @Email(message = "Email not valid")
    private String email;

    @Getter
    @Setter
    private String timezone;

    @Getter
    @Setter
    private boolean metric;

    @Getter
    @Setter
    private TrackData.Sharing sharing;
}