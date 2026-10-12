package com.cubetrek.upload.garminconnect;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.Users;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.basic.DefaultOAuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class GarminconnectController {
    private static final Logger logger = LoggerFactory.getLogger(GarminconnectController.class);
    private final String consumerKey;
    private final String consumerSecret;
    private final String callbackUrl;
    private final boolean oauth2SignupEnabled;
    private final GarminconnectAuthSession session;
    private final GarminApiClient api;
    private final GarminConnectionService connections;

    public GarminconnectController(@Value("${garmin.consumer.key}") String consumerKey,
                                   @Value("${garmin.consumer.secret}") String consumerSecret,
                                   @Value("${cubetrek.address}") String address,
                                   @Value("${garmin.oauth2.signup-enabled:false}") boolean oauth2SignupEnabled,
                                   GarminconnectAuthSession session, GarminApiClient api, GarminConnectionService connections) {
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.callbackUrl = address.replaceAll("/+$", "") + "/profile/connectGarmin-step2";
        this.oauth2SignupEnabled = oauth2SignupEnabled;
        this.session = session;
        this.api = api;
        this.connections = connections;
    }

    @GetMapping("/profile/connectGarmin-step1")
    public String connectToGarmin(Authentication authentication) {
        Users user = (Users) authentication.getPrincipal();
        try {
            if (oauth2SignupEnabled) {
                String clientId = api.oauth2ClientId();
                var pending = session.beginOAuth2(user.getId(), callbackUrl);
                return "redirect:" + UriComponentsBuilder.fromUriString("https://connect.garmin.com/oauth2Confirm")
                        .queryParam("response_type", "code").queryParam("client_id", clientId)
                        .queryParam("state", pending.state()).queryParam("redirect_uri", callbackUrl)
                        .queryParam("code_challenge", pending.challenge()).queryParam("code_challenge_method", "S256")
                        .build().encode().toUriString();
            }
            OAuthConsumer consumer = new DefaultOAuthConsumer(consumerKey, consumerSecret);
            OAuthProvider provider = new DefaultOAuthProvider(
                    "https://connectapi.garmin.com/oauth-service/oauth/request_token",
                    "https://connectapi.garmin.com/oauth-service/oauth/access_token",
                    "https://connect.garmin.com/oauthConfirm");
            String url = provider.retrieveRequestToken(consumer, callbackUrl);
            session.beginOAuth1(user.getId(), consumer, provider);
            return "redirect:" + url + "&oauth_callback="
                    + java.net.URLEncoder.encode(callbackUrl, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("Garmin authorization could not start: user={}, errorType={}", user.getId(), e.getClass().getSimpleName());
            throw new ExceptionHandling.UnnamedException("Failed", "Cannot connect to Garmin. Please try again.");
        }
    }

    @GetMapping("/profile/connectGarmin-step2")
    public String connectToGarmin2(Authentication authentication,
                                  @RequestParam(required = false) String oauth_token,
                                  @RequestParam(required = false) String oauth_verifier,
                                  @RequestParam(required = false) String code,
                                  @RequestParam(required = false) String state,
                                  @RequestParam(required = false) String error) {
        Users user = (Users) authentication.getPrincipal();
        try {
            if (state != null || code != null || error != null) {
                var pending = session.consumeOAuth2(user.getId(), state);
                if (error != null || code == null || code.isBlank()) {
                    throw new IllegalArgumentException("Garmin consent was declined");
                }
                GarminTokens tokens = api.exchangeCode(code, pending.verifier(), pending.redirectUri());
                String userId = api.userId(tokens.accessToken());
                boolean allowed = api.activityExportAllowed(tokens.accessToken());
                connections.linkOAuth2(user, userId, tokens, allowed);
            } else {
                var pending = session.consumeOAuth1(user.getId(), oauth_token);
                if (oauth_verifier == null || oauth_verifier.isBlank()) {
                    throw new IllegalArgumentException("Missing Garmin OAuth verifier");
                }
                pending.provider().retrieveAccessToken(pending.consumer(), oauth_verifier);
                String token = pending.consumer().getToken();
                String secret = pending.consumer().getTokenSecret();
                connections.linkOAuth1(user, api.userIdOAuth1(token, secret), token, secret);
            }
            logger.info("Garmin account linked: user={}", user.getId());
            return "redirect:/profile";
        } catch (ExceptionHandling.UnnamedException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            logger.warn("Garmin account link failed: user={}, errorType={}", user.getId(), e.getClass().getSimpleName());
            throw new ExceptionHandling.UnnamedException("Failed", "Cannot link Garmin account. Please start the connection again.");
        }
    }
}
