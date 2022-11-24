package com.cubetrek.upload.garminconnect;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Objects;

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



    @GetMapping(value="/profile/connectGarmin-step1")
    public String connectToGarmin() {
        final String callbackUrl = httpAddress+"/profile/connectGarmin-step2";
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        logger.info("GarminConnect User tries to Link Garmin Account: User id '"+user.getId()+"'");
        OAuthConsumer consumer = new DefaultOAuthConsumer(garminConsumerKey, garminConsumerSecret);
        OAuthProvider provider = new DefaultOAuthProvider(requestTokenEndpointUrl, accessTokenEndpointUrl, authorizeWebsiteUrl);
        garminconnectAuthSession.setOAuthConsumer(consumer);
        garminconnectAuthSession.setOAuthProvider(provider); //store provider for this session to be reused on step2
        try {
            String url = provider.retrieveRequestToken(consumer, callbackUrl);
            url += "&oauth_callback="+callbackUrl; // no clue why this is not automatically added
            return "redirect:"+url;
        } catch (OAuthMessageSignerException | OAuthNotAuthorizedException | OAuthExpectationFailedException |
                 OAuthCommunicationException e) {
            logger.error("Connect to Garmin - Step 1: User id '"+user.getId()+"'", e);
            throw new ExceptionHandling.TrackViewerException("Error: Cannot connect to Garmin");
        }
    }

    @GetMapping(value="/profile/connectGarmin-step2")
    public String connectToGarmin2(@RequestParam("oauth_token") String oauth_token, @RequestParam("oauth_verifier") String oauth_verifier) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();

        OAuthConsumer consumer = garminconnectAuthSession.getOAuthConsumer();
        OAuthProvider provider = garminconnectAuthSession.getOAuthProvider();

        try {
            provider.retrieveAccessToken(consumer, oauth_verifier);

            String userAccessToken = consumer.getToken();
            String userAccessTokenSecret = consumer.getTokenSecret();

            UserThirdpartyConnect userThirdpartyConnect = userThirdpartyConnectRepository.findByUser(user);

            UserThirdpartyConnect other = userThirdpartyConnectRepository.findByGarminUseraccesstoken(userAccessToken);

            if ((other != null && userThirdpartyConnect != null && !Objects.equals(other.getId(), userThirdpartyConnect.getId())) || other != null && userThirdpartyConnect == null) {
                throw new ExceptionHandling.UnnamedException("Failed", "This Garmin Account is already linked to another CubeTrek account");
            }



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
            logger.info("GarminConnect User successfully linked Garmin Account: User id '"+user.getId()+"'; Garmin User Access Token: '"+userAccessToken+"'");
            return "redirect:/profile";
        } catch (OAuthMessageSignerException | OAuthNotAuthorizedException | OAuthExpectationFailedException |
                 OAuthCommunicationException e) {
            logger.error("Connect to Garmin - Step 2: User id '"+user.getId()+"'", e);
            throw new ExceptionHandling.UnnamedException("Failed", "Cannot link Account to Garmin");
        }
    }
}
