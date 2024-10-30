package com.cubetrek.upload.corosapi;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.util.Objects;

@Controller
public class CorosconnectController {
    Logger logger = LoggerFactory.getLogger(CorosconnectController.class);

    @Autowired
    ApplicationEventPublisher eventPublisher;


    @Value("${coros.client.id}") //aka application_id
    String corosClientId;
    @Value("${coros.client.secret}") //aka accesstoken
    String corosClientSecret;

    @Value("${cubetrek.address}")
    private String httpAddress;

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;

    //Get the Authentications

    //final String corosBaseURL = "https://opentest.coros.com/"; //test
    public final static String corosBaseURL = "https://open.coros.com/"; //live


    @GetMapping(value="/profile/connectCoros-step1")
    public String connectToCoros(HttpSession session) {
        final String requestURLCoros = corosBaseURL+"oauth2/authorize?client_id="+corosClientId;
        final String redirectUri = httpAddress+"/profile/connectCoros-step2";

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        logger.info("CorosConnect User tries to Link Coros Account: User id '"+user.getId()+"'");

        String state = createRandomString();
        session.setAttribute("corosState", state);
        String authorizationUrl = requestURLCoros + "&redirect_uri=" + redirectUri + "&response_type=code&state=" + state;
        return "redirect:"+authorizationUrl;
    }

    @GetMapping(value="/profile/connectCoros-step2")
    public String connectToCoros2(@RequestParam("code") String code, @RequestParam("state") String state, HttpSession session) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        final String redirectUri = httpAddress+"/profile/connectCoros-step2";

        if (code == null) {
            logger.error("Error linking Coros account 1. User id: "+user.getId());
            throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Coros account.");
        }

        String storedString = (String) session.getAttribute("corosState");

        if (storedString == null || !storedString.equals(state)) {
            logger.error("Error linking Coros account 5. User id: "+user.getId());
            throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Coros account.");
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", corosClientId);
            body.add("client_secret", corosClientSecret);
            body.add("redirect_uri", redirectUri);
            body.add("code", code);
            body.add("grant_type", "authorization_code");
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);


            ResponseEntity<String> response = restTemplate.exchange(corosBaseURL+"oauth2/accesstoken", HttpMethod.POST, request, String.class);
            //logger.info("response string: "+response.getBody());

            // Convert the raw JSON string into the RequestReturn object
            ObjectMapper objectMapper = new ObjectMapper();
            RequestReturn requestReturn = objectMapper.readValue(response.getBody(), RequestReturn.class);

            if (response.getStatusCode() != HttpStatus.OK || requestReturn == null || !requestReturn.isValid()) {
                logger.error("Error linking Coros account 2. User id: " + user.getId() + "; " + response.getBody());
                throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Coros account. Please make sure to select all relevant checkmarks.");
            }

            UserThirdpartyConnect userThirdpartyConnect = userThirdpartyConnectRepository.findByUser(user);
            UserThirdpartyConnect other = userThirdpartyConnectRepository.findByCorosUserid(requestReturn.openId);

            if ((other != null && userThirdpartyConnect != null && !Objects.equals(other.getId(), userThirdpartyConnect.getId())) || other != null && userThirdpartyConnect == null) {
                throw new ExceptionHandling.UnnamedException("Failed", "This Coros Account is already linked to another CubeTrek account");
            }

            if (userThirdpartyConnect==null) {
                UserThirdpartyConnect utc = new UserThirdpartyConnect();
                utc.setCorosEnabled(true);
                utc.setCorosUserid(requestReturn.openId);
                utc.setCorosAccessToken(requestReturn.accessToken);
                utc.setCorosRefreshToken(requestReturn.refreshToken);
                utc.setCorosExpiresIn(requestReturn.expiresIn);
                utc.setUser(user);
                userThirdpartyConnectRepository.save(utc);
            } else {
                userThirdpartyConnect.setCorosEnabled(true);
                userThirdpartyConnect.setCorosUserid(requestReturn.openId);
                userThirdpartyConnect.setCorosAccessToken(requestReturn.accessToken);
                userThirdpartyConnect.setCorosRefreshToken(requestReturn.refreshToken);
                userThirdpartyConnect.setCorosExpiresIn(requestReturn.expiresIn);
                userThirdpartyConnectRepository.save(userThirdpartyConnect);
            }
            userThirdpartyConnectRepository.flush();

            logger.info("Coros User successfully linked Coros Account: User id '"+user.getId()+"'; Coros User id: '"+requestReturn.openId+"'");

            //Request historical data for this newly registered user
            eventPublisher.publishEvent(new CorosHistoricDataRequestedListener.OnEvent(user, userThirdpartyConnectRepository.findByUser(user)));

            return "redirect:/profile";
        } catch (ExceptionHandling.UnnamedException ue) {
            throw ue;
        } catch(Exception e) {
            logger.error("Error linking Coros account 4. User id: "+user.getId(), e);
            throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Coros account.");
        }
    }

    static class RequestReturn {
        @JsonProperty("expires_in")
        Integer expiresIn;

        @JsonProperty("refresh_token")
        String refreshToken;

        @JsonProperty("access_token")
        String accessToken;

        @JsonProperty("openId")
        String openId;

        boolean isValid() {
            return expiresIn != null && refreshToken != null && accessToken != null && openId != null;
        }
    }

    @GetMapping(value="/profile/connectCoros-historic")
    public String getHistoricData() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        UserThirdpartyConnect utc = userThirdpartyConnectRepository.findByUser(user);

        eventPublisher.publishEvent(new CorosHistoricDataRequestedListener.OnEvent(user, utc));

        return "redirect:/profile";
    }

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private String createRandomString() {
        StringBuilder stringBuilder = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            int randomIndex = RANDOM.nextInt(CHARACTERS.length());
            stringBuilder.append(CHARACTERS.charAt(randomIndex));
        }
        return stringBuilder.toString();
    }
}
