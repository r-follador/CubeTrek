package com.cubetrek.upload;

import com.sunlocator.topolibrary.LatLon;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

@Getter
@Setter
public class OnNewUploadEvent extends ApplicationEvent {

    long trackId;
    LatLon highestPoint;

    String timezone;


    public OnNewUploadEvent(long trackId, LatLon highestPoint, String timezone) {
        super(trackId);
        this.trackId = trackId;
        this.highestPoint = highestPoint;
        this.timezone = timezone;
    }
}
