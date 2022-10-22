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
        logger.info("GarminConnect Ping received: "+payload);
        try {
            boolean success = false;
            JsonNode activities = (new ObjectMapper()).readTree(payload).get("activityFiles");
            JsonNode deregistrations = (new ObjectMapper()).readTree(payload).get("deregistrations");
            JsonNode userPermissionsChange = (new ObjectMapper()).readTree(payload).get("userPermissionsChange");
            if (activities != null && !activities.isNull() && !activities.isEmpty() && activities.isArray()) {
                success = true;
                for (final JsonNode activityNode : activities){
                    String activityId = activityNode.path("activityId").asText("blarg");
                    String userAccessToken = activityNode.path("userAccessToken").asText("blarg");
                    String callbackURL = activityNode.path("callbackURL").asText("blarg");
                    String fileType = activityNode.path("fileType").asText("blarg");

                    if (activityId.equals("blarg") || userAccessToken.equals("blarg") || callbackURL.equals("blarg") || fileType.equals("blarg")) {
                        logger.warn("GarminConnect Ping: malformed ActivityFile Ping");
                        return ResponseEntity.badRequest().build();
                    }
                    //Publish async event; handled by GarminNewFileEventListener
                    eventPublisher.publishEvent(new OnNewGarminFileEvent(activityId, userAccessToken, callbackURL, fileType));
                }
            }
            if (deregistrations != null && !deregistrations.isNull() && !deregistrations.isEmpty() && deregistrations.isArray()) {
                success = true;
                for (final JsonNode deregNode : deregistrations){
                    String userAccessToken = deregNode.path("userAccessToken").asText("blarg");
                    if (userAccessToken.equals("blarg")) {
                        logger.warn("GarminConnect Ping: malformed Deregistration Ping");
                        return ResponseEntity.badRequest().build();
                    }
                    //Publish async event; handled by GarminNewReregistrationEventListener
                    eventPublisher.publishEvent(new OnNewGarminDeregistrationEvent(userAccessToken, false));
                }
            }
            if (userPermissionsChange != null && !userPermissionsChange.isNull() && !userPermissionsChange.isEmpty() && userPermissionsChange.isArray()) {
                success = true;
                for (final JsonNode deregNode : userPermissionsChange){
                    String userAccessToken = deregNode.path("userAccessToken").asText("blarg");
                    String permissions = deregNode.path("permissions").toString();

                    if (userAccessToken.equals("blarg") || permissions.equals("blarg")) {
                        logger.warn("GarminConnect Ping: malformed userPermissionChange Ping");
                        return ResponseEntity.badRequest().build();
                    }
                    boolean isEnabled = permissions.contains("ACTIVITY_EXPORT");
                    //Publish async event; handled by GarminNewReregistrationEventListener
                    eventPublisher.publishEvent(new OnNewGarminDeregistrationEvent(userAccessToken, isEnabled));
                }
            }
            if (success) {
                //logger.info("GarminConnect: Successfully parsed request");
                return ResponseEntity.ok().build();
            } else {
                logger.info("GarminConnect Ping: cannot parse");
                return ResponseEntity.ok().build() ;
            }
        } catch (JsonProcessingException e) {
            logger.error("GarminConnect Ping: JsonProcessingException", e);
            throw new RuntimeException(e);
        }
    }
}
