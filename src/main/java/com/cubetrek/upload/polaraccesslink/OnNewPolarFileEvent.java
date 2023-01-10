package com.cubetrek.upload.polaraccesslink;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

@Getter
@Setter
public class OnNewPolarFileEvent extends ApplicationEvent {

    String entityId;
    String userId;
    String url;

    public OnNewPolarFileEvent(String entityId, String userId, String url) {
        super(userId);
        this.entityId = entityId;
        this.userId = userId;
        this.url = url;
    }
}
