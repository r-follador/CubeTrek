package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GarminconnectController {
    Logger logger = LoggerFactory.getLogger(GarminconnectController.class);

    @Value("${garmin.consumer.key}")
    String garminConsumerKey;
    @Value("${garmin.consumer.secret}")
    String garminConsumerSecret;

    @Value("${cubetrek.address}")
    private String httpAddress;

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;

    @Autowired
    private GarminconnectAuthSession garminconnectAuthSession;

    //Get the Authentications

    final String requestTokenEndpointUrl = "https://connectapi.garmin.com/oauth-service/oauth/request_token";
    final String accessTokenEndpointUrl = "https://connectapi.garmin.com/oauth-service/oauth/access_token";
    final String authorizeWebsiteUrl = "https://connect.garmin.com/oauthConfirm";


    final String callbackUrl = httpAddress+"/profile/connectGarmin-step2";

    @GetMapping(value="/profile/connectGarmin-step1")
    public String connectToGarmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();

        OAuthConsumer consumer = new DefaultOAuthConsumer(garminConsumerKey, garminConsumerSecret);
        OAuthProvider provider = new DefaultOAuthProvider(requestTokenEndpointUrl, accessTokenEndpointUrl, authorizeWebsiteUrl);
        garminconnectAuthSession.setOAuthConsumer(consumer);
        garminconnectAuthSession.setOAuthProvider(provider); //store provider for this session to be reused on step2
        try {
            String url = provider.retrieveRequestToken(consumer, callbackUrl);
            return "redirect:"+url;
        } catch (OAuthMessageSignerException | OAuthNotAuthorizedException | OAuthExpectationFailedException |
                 OAuthCommunicationException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping(value="/profile/connectGarmin-step2")
    public void connectToGarmin2(@RequestParam("oauth_token") String oauth_token, @RequestParam("oauth_verifier") String oauth_verifier) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();

        OAuthConsumer consumer = garminconnectAuthSession.getOAuthConsumer();
        OAuthProvider provider = garminconnectAuthSession.getOAuthProvider();

        try {
            provider.retrieveAccessToken(consumer, oauth_verifier);

            String userAccessToken = consumer.getToken();
            String userAccessTokenSecret = consumer.getTokenSecret();

            UserThirdpartyConnect userThirdpartyConnect = userThirdpartyConnectRepository.findByUser(user);

            if (userThirdpartyConnect==null) {
                UserThirdpartyConnect utc = new UserThirdpartyConnect();
                utc.setGarminEnabled(true);
                utc.setGarminUseraccesstoken(userAccessToken);
                utc.setGarminUseraccesstokenSecret(userAccessTokenSecret);
                utc.setUser(user);
                userThirdpartyConnectRepository.save(utc);
            } else {
                userThirdpartyConnect.setGarminEnabled(true);
                userThirdpartyConnect.setGarminUseraccesstoken(userAccessToken);
                userThirdpartyConnect.setGarminUseraccesstokenSecret(userAccessTokenSecret);
                userThirdpartyConnectRepository.save(userThirdpartyConnect);
            }

        } catch (OAuthMessageSignerException | OAuthNotAuthorizedException | OAuthExpectationFailedException |
                 OAuthCommunicationException e) {
            throw new RuntimeException(e);
        }


    }


}
