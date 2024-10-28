package com.cubetrek.upload.corosapi;

import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Service
public class CorosScheduledRefresher {

    Logger logger = LoggerFactory.getLogger(CorosScheduledRefresher.class);

    @Value("${coros.client.id}") //aka application_id
    String corosClientId;
    @Value("${coros.client.secret}") //aka accesstoken
    String corosClientSecret;

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.DAYS, initialDelay = 15)
    public void refreshUserTokens() {
        //Refreshing the AccessToken of every registered user each 20 days
        //see Chapter 3.5

        logger.info("Coros: Scheduled Refresher Task");

        List<UserThirdpartyConnect> utcs = userThirdpartyConnectRepository.findByCorosUseridIsNotNull();

        for (UserThirdpartyConnect utc : utcs) {
            logger.info("- Coros Refresh Token for user id {}", utc.getUser().getId());

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", corosClientId);
            body.add("client_secret", corosClientSecret);
            body.add("grant_type", "refresh_token");
            body.add("refresh_token", utc.getCorosRefreshToken());
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(CorosconnectController.corosBaseURL+"oauth2/refresh-token", HttpMethod.POST, request, Map.class);
            Map<String, String> responseBody = response.getBody();

            //Response is {result=0000, message=OK}; no clue what to do with it
            logger.info("Coros response: "+responseBody);
        }
    }
}
