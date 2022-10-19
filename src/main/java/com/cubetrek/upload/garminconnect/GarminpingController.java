package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.Users;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Controller
public class GarminpingController {
    Logger logger = LoggerFactory.getLogger(GarminpingController.class);

    @Value("${garmin.consumer.key}")
    String garminConsumerKey;
    @Value("${garmin.consumer.secret}")
    String garminConsumerSecret;

    @Autowired
    ApplicationEventPublisher eventPublisher;


    //Ping from Garmin
    @PostMapping(value = "/garminconnect")
    public ResponseEntity uploadFile(@RequestBody String payload) {
        logger.info("GarminConnect Hook called: "+payload);
        try {
            JsonNode activities = (new ObjectMapper()).readTree(payload).get("activityFiles");
            if (activities == null || activities.isNull() || activities.isEmpty()) {
                logger.info("GarminConnect: cannot parse");
                return ResponseEntity.ok().build() ;
            }
            if (activities.isArray()) {
                for (final JsonNode acitivityNode : activities){
                    String activityId = acitivityNode.path("activityId").asText("blarg");
                    String userAccessToken = acitivityNode.path("userAccessToken").asText("blarg");
                    String callbackURL = acitivityNode.path("callbackURL").asText("blarg");
                    String fileType = acitivityNode.path("fileType").asText("blarg");

                    if (activityId.equals("blarg") || userAccessToken.equals("blarg") || callbackURL.equals("blarg") || fileType.equals("blarg")) {
                        logger.warn("GarminConnect: malformed Ping");
                        return ResponseEntity.badRequest().build();
                    }
                    eventPublisher.publishEvent(new OnNewGarminFileEvent(activityId, userAccessToken, callbackURL, fileType));
                }
            }


        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        logger.warn("GarminConnect: error processing ping");
        return ResponseEntity.badRequest().build();
    }
}
