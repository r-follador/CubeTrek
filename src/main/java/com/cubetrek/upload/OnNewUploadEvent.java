package com.cubetrek.upload;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

@Getter
@Setter
public class OnNewUploadEvent extends ApplicationEvent {

    long trackId;


    public OnNewUploadEvent(long trackId) {
        super(trackId);
        this.trackId = trackId;
    }
}
