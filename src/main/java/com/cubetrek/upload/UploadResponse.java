package com.cubetrek.upload;

import com.cubetrek.database.TrackMetadata;
import com.sunlocator.topolibrary.GPX.GPXWorker;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

public class UploadResponse {

    @Getter
    @Setter
    public long trackID;

    @Getter
    @Setter
    public String title;

    @Getter
    @Setter
    public ZonedDateTime date;

    @Getter
    @Setter
    public TrackMetadata.Activitytype activitytype;

    @Getter
    @Setter
    public GPXWorker.TrackSummary trackSummary;

}
