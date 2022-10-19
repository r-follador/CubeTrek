package com.cubetrek.upload.garminconnect;

import lombok.Getter;
import lombok.Setter;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@Scope(value = "session",proxyMode = ScopedProxyMode.TARGET_CLASS)
@Getter
@Setter
public class GarminconnectAuthSession {
    OAuthProvider oAuthProvider;
    OAuthConsumer oAuthConsumer;
}
