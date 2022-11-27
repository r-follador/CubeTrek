package com.cubetrek.upload;

import com.cubetrek.database.TrackData;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.util.HtmlUtils;

public class EditTrackmetadataDto {

    @Getter
    @Setter
    private Long index;

    @Getter
    @Setter
    private String title;

    @Getter
    @Setter
    private TrackData.Activitytype activitytype;

    @Getter
    @Setter
    private String note;

    @Getter
    private String errorMessage;

    public boolean check() {
        if (index == null) {
            errorMessage = "Error: Wrong track";
            return false;
        }

        if (title != null && title.isBlank()) {
            errorMessage = "Title cannot be empty";
            return false;
        }

        if (title != null && title.length() > 250) {
            errorMessage = "Title is too long (max. 250 characters)";
            return false;
        }
        return true;
    }
}
