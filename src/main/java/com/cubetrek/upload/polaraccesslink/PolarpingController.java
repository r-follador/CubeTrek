package com.cubetrek.upload.polaraccesslink;

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
public class PolarpingController {
    Logger logger = LoggerFactory.getLogger(PolarpingController.class);

    @Value("${polar.client.id}")
    String polarConsumerKey;
    @Value("${polar.client.secret}")
    String polarConsumerSecret;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    //Ping from Polar; this is the webhook
    @PostMapping(value = "/polarconnect")
    public ResponseEntity polarPing(@RequestBody String payload) {
        logger.info("PolarAccesslink Ping received: "+payload);

        try {
            JsonNode activities = (new ObjectMapper()).readTree(payload);

            if (activities != null && !activities.isNull() && !activities.isEmpty()) {
                String event = Optional.ofNullable(activities.get("event")).map(JsonNode::asText).orElse("blarg");
                String user_id = Optional.ofNullable(activities.get("user_id")).map(JsonNode::asText).orElse("blarg");
                String entity_id = Optional.ofNullable(activities.get("entity_id")).map(JsonNode::asText).orElse("blarg");
                String timestamp = Optional.ofNullable(activities.get("timestamp")).map(JsonNode::asText).orElse("blarg");
                String url = Optional.ofNullable(activities.get("url")).map(JsonNode::asText).orElse("blarg");

                if (event.equals("blarg") || user_id.equals("blarg") || entity_id.equals("blarg") || timestamp.equals("blarg") || url.equals("blarg")) {
                    logger.warn("PolarAcceslink Ping: malformed Ping, missing keys; Payload: "+payload);
                    return ResponseEntity.badRequest().build();
                }
                if (event.equals("EXERCISE"))
                    eventPublisher.publishEvent(new PolarNewFileEventListener.OnEvent(entity_id, user_id, url, payload));
                else
                    logger.info("PolarAccesslink Ping: Not an EXERCISE event, ignored; event is: "+event +"; Payload: "+payload);

                return ResponseEntity.ok().build();
            } else {
                logger.info("PolarAccesslink Ping: cannot parse; Payload: "+payload);
                return ResponseEntity.ok().build() ;
            }
        } catch (JsonProcessingException e) {
            logger.error("PolarAccesslink Ping: JsonProcessingException; Payload: "+payload, e);
            return ResponseEntity.ok().build();
        }
    }
}
