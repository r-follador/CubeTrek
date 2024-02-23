package com.cubetrek.upload.garminconnect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Optional;

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
        logger.info("Garminconnect  Ping received: "+payload);
        try {
            boolean success = false;
            JsonNode activities = (new ObjectMapper()).readTree(payload).get("activityFiles");
            JsonNode deregistrations = (new ObjectMapper()).readTree(payload).get("deregistrations");
            JsonNode userPermissionsChange = (new ObjectMapper()).readTree(payload).get("userPermissionsChange");
            if (activities != null && !activities.isNull() && !activities.isEmpty() && activities.isArray()) {
                success = true;
                for (final JsonNode activityNode : activities){
                    String activityId = Optional.ofNullable(activityNode.path("activityId")).map(JsonNode::asText).orElse("blarg");
                    String userAccessToken =  Optional.ofNullable(activityNode.path("userAccessToken")).map(JsonNode::asText).orElse("blarg");
                    String callbackURL =  Optional.ofNullable(activityNode.path("callbackURL")).map(JsonNode::asText).orElse("blarg");
                    String fileType =  Optional.ofNullable(activityNode.path("fileType")).map(JsonNode::asText).orElse("blarg");

                    if (activityId.equals("blarg") || userAccessToken.equals("blarg") || callbackURL.equals("blarg") || fileType.equals("blarg")) {
                        logger.warn("GarminConnect Ping: malformed ActivityFile Ping, missing keys; Payload is: "+payload);
                        return ResponseEntity.badRequest().build();
                    }
                    //Publish async event; handled by GarminNewFileEventListener
                    eventPublisher.publishEvent(new GarminNewFileEventListener.OnEvent(activityId, userAccessToken, callbackURL, fileType, payload));
                }
            }
            if (deregistrations != null && !deregistrations.isNull() && !deregistrations.isEmpty() && deregistrations.isArray()) {
                success = true;
                for (final JsonNode deregNode : deregistrations){
                    String userAccessToken = Optional.ofNullable(deregNode.path("userAccessToken")).map(JsonNode::asText).orElse("blarg");
                    if (userAccessToken.equals("blarg")) {
                        logger.warn("GarminConnect Ping: malformed Deregistration Ping; Payload: "+payload);
                        return ResponseEntity.badRequest().build();
                    }
                    //Publish async event; handled by GarminNewReregistrationEventListener
                    eventPublisher.publishEvent(new GarminNewDeregistrationEventListener.OnEvent(userAccessToken, false, payload));
                }
            }
            if (userPermissionsChange != null && !userPermissionsChange.isNull() && !userPermissionsChange.isEmpty() && userPermissionsChange.isArray()) {
                success = true;
                for (final JsonNode deregNode : userPermissionsChange){
                    String userAccessToken = Optional.ofNullable(deregNode.path("userAccessToken")).map(JsonNode::asText).orElse("blarg");
                    String permissions = Optional.ofNullable(deregNode.path("permissions")).map(JsonNode::asText).orElse("blarg");

                    if (userAccessToken.equals("blarg") || permissions.equals("blarg")) {
                        logger.warn("GarminConnect Ping: malformed userPermissionChange Ping; Payload: "+payload);
                        return ResponseEntity.badRequest().build();
                    }
                    boolean isEnabled = permissions.contains("ACTIVITY_EXPORT");
                    //Publish async event; handled by GarminNewReregistrationEventListener
                    eventPublisher.publishEvent(new GarminNewDeregistrationEventListener.OnEvent(userAccessToken, isEnabled, payload));
                }
            }
            if (success) {
                return ResponseEntity.ok().build();
            } else {
                logger.info("GarminConnect Ping: cannot parse; Payload: "+payload);
                return ResponseEntity.ok().build() ;
            }
        } catch (JsonProcessingException e) {
            logger.error("GarminConnect Ping: JsonProcessingException; Payload: "+payload, e);
            return ResponseEntity.ok().build();
        }
    }
}
