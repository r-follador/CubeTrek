package com.cubetrek.upload.corosapi;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import com.cubetrek.upload.garminconnect.GarminconnectAuthSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Controller
public class CorosconnectController {
    Logger logger = LoggerFactory.getLogger(CorosconnectController.class);

    @Value("${coros.client.id}") //aka application_id
    String corosClientId;
    @Value("${coros.client.secret}") //aka accesstoken
    String corosClientSecret;

    @Value("${cubetrek.address}")
    private String httpAddress;

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;

    @Autowired
    private GarminconnectAuthSession garminconnectAuthSession;

    //Get the Authentications

    final String requestURLCoros = "https://open.coros.com/oauth2/authorize?client_id="+corosClientId;

    final String redirectUri = httpAddress+"/profile/connectCoros-step2";


    @GetMapping(value="/profile/connectCoros-step1")
    public String connectToCoros() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        logger.info("CorosConnect User tries to Link Coros Account: User id '"+user.getId()+"'");

        String state = "555xxx444"; // TODO: use a random generator here
        String authorizationUrl = requestURLCoros + "&redirect_uri=" + redirectUri + "&response_type=code&state=" + state;
        return "redirect:"+authorizationUrl;
    }

    @GetMapping(value="/profile/connectCoros-step2")
    public String connectToCoros2(@RequestParam("code") String code, @RequestParam("state") String state) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();

        //TODO: remove in working system
        logger.info("connectCoros-step2: State: "+state);
        logger.info("connectCoros-step2: Code: "+code);

        if (code == null) {
            logger.error("Error linking Coros account 1. User id: "+user.getId());
            throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Coros account.");
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            Map<String, String> body = new HashMap<>();
            body.put("client_id", corosClientId);
            body.put("client_secret", corosClientSecret);
            body.put("redirect_uri", redirectUri);
            body.put("code", code);
            body.put("grant_type", "authorization_code");
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);


            ResponseEntity<RequestReturn> response = restTemplate.exchange("https://open.coros.com/oauth2/accesstoken", HttpMethod.POST, request, RequestReturn.class);
            RequestReturn responseBody = response.getBody();

            if (response.getStatusCode() != HttpStatus.OK || responseBody == null || !responseBody.isValid()) {
                logger.error("Error linking Coros account 2. User id: " + user.getId() + "; " + response.getBody());
                throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Coros account.");
            }

            UserThirdpartyConnect userThirdpartyConnect = userThirdpartyConnectRepository.findByUser(user);
            UserThirdpartyConnect other = userThirdpartyConnectRepository.findByCorosUserid(responseBody.openId);

            if ((other != null && userThirdpartyConnect != null && !Objects.equals(other.getId(), userThirdpartyConnect.getId())) || other != null && userThirdpartyConnect == null) {
                throw new ExceptionHandling.UnnamedException("Failed", "This Coros Account is already linked to another CubeTrek account");
            }

            if (userThirdpartyConnect==null) {
                UserThirdpartyConnect utc = new UserThirdpartyConnect();
                utc.setCorosEnabled(true);
                utc.setCorosUserid(responseBody.openId);
                utc.setCorosAccessToken(responseBody.accessToken);
                utc.setCorosRefreshToken(responseBody.refreshToken);
                utc.setCorosExpiresIn(responseBody.expiresIn);
                utc.setUser(user);
                userThirdpartyConnectRepository.save(utc);
            } else {
                userThirdpartyConnect.setCorosEnabled(true);
                userThirdpartyConnect.setCorosUserid(responseBody.openId);
                userThirdpartyConnect.setCorosAccessToken(responseBody.accessToken);
                userThirdpartyConnect.setCorosRefreshToken(responseBody.refreshToken);
                userThirdpartyConnect.setCorosExpiresIn(responseBody.expiresIn);
                userThirdpartyConnectRepository.save(userThirdpartyConnect);
            }

            logger.info("Coros User successfully linked Coros Account: User id '"+user.getId()+"'; Coros User id: '"+responseBody.openId+"'");

            return "redirect:/profile";
        } catch(Exception e) {
            logger.error("Error linking Coros account 4. User id: "+user.getId(), e);
            throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Coros account.");
        }
    }

    static class RequestReturn {
        Integer expiresIn;
        String refreshToken;
        String accessToken;
        String openId;

        boolean isValid() {
            return expiresIn != null && refreshToken != null && accessToken != null && openId != null;
        }
    }


    @GetMapping(value="/profile/connectCoros-refresh")
    public String refreshToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        UserThirdpartyConnect utc = userThirdpartyConnectRepository.findByUser(user);

        logger.info("Refresh called for user id "+user.getId());

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        Map<String, String> body = new HashMap<>();
        body.put("client_id", corosClientId);
        body.put("client_secret", corosClientSecret);
        body.put("grant_type", "refresh_token");
        body.put("refresh_token", utc.getCorosRefreshToken());

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange("https://open.coros.com/oauth2/refresh-token", HttpMethod.POST, request, Map.class);
        Map<String, String> responseBody = response.getBody();

        //unclear what the response is
        logger.info("coros response: "+responseBody);

        return "refreshed";
    }
}
