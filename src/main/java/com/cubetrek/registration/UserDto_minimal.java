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

public class UserDto_minimal {
    @Getter
    @Setter
    @NotNull
    @NotBlank(message = "Name cannot be empty")
    @Size(min=1, max=255, message = "Name must be between 1 and 255 characters")
    private String name;

    @Getter
    @Setter
    private String email;

    @Getter
    @Setter
    private TrackData.Sharing sharing;
}