package com.cubetrek.upload;

import com.cubetrek.database.TrackMetadata;
import lombok.Getter;
import lombok.Setter;

public class EditTrackmetadataDto {

    @Getter
    @Setter
    public long index;

    @Getter
    @Setter
    public String title;

    @Getter
    @Setter
    public TrackMetadata.Activitytype activitytype;

    @Getter
    @Setter
    public String note;

    @Getter
    String errorMessage;

    public boolean check() {
        if (title == null || title.isBlank()) {
            errorMessage = "Title cannot be empty";
            return false;
        }

        if (title.length() > 250) {
            errorMessage = "Title is too long (max. 250 characters)";
            return false;
        }

        if (activitytype == null) {
            errorMessage = "Activity Type cannot be empty";
            return false;
        }

        return true;
    }
}
