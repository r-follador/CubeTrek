package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.Users;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;
import org.springframework.web.bind.annotation.GetMapping;

@Getter
@Setter
public class OnNewGarminFileEvent extends ApplicationEvent {

    String activityId;
    String userAccessToken;
    String callbackURL;
    String fileType;

    public OnNewGarminFileEvent(String activityId, String userAccessToken, String callbackURL, String fileType) {
        super(userAccessToken);
        this.activityId = activityId;
        this.userAccessToken = userAccessToken;
        this.callbackURL = callbackURL;
        this.fileType = fileType;
    }
}
