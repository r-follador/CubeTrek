package com.cubetrek.upload.garminconnect;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

@Getter
@Setter
public class OnNewGarminDeregistrationEvent extends ApplicationEvent {

    String userAccessToken;

    boolean isEnabled;

    public OnNewGarminDeregistrationEvent(String userAccessToken, boolean isEnabled) {
        super(userAccessToken);
        this.userAccessToken = userAccessToken;
        this.isEnabled = isEnabled;
    }
}
