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
            boolean success = false;
            JsonNode activities = (new ObjectMapper()).readTree(payload).get("EXCERCISE");

            if (activities != null && !activities.isNull() && !activities.isEmpty()) {
                success = true;
                String user_id = (new ObjectMapper()).readTree(payload).get("user_id").asText("blarg");
                String entity_id = (new ObjectMapper()).readTree(payload).get("entity_id").asText("blarg");
                String timestamp = (new ObjectMapper()).readTree(payload).get("timestamp").asText("blarg");
                String url = (new ObjectMapper()).readTree(payload).get("url").asText("blarg");

                if (user_id.equals("blarg") || entity_id.equals("blarg") || timestamp.equals("blarg") || url.equals("blarg")) {
                    logger.warn("PolarAcceslink Ping: malformed Excercise Ping");
                    return ResponseEntity.badRequest().build();
                }
                eventPublisher.publishEvent(new PolarNewFileEventListener.OnEvent(entity_id, user_id, url));
            }
            if (success) {
                return ResponseEntity.ok().build();
            } else {
                logger.info("PolarAccesslink Ping: cannot parse");
                return ResponseEntity.ok().build() ;
            }
        } catch (JsonProcessingException e) {
            logger.error("PolarAccesslink Ping: JsonProcessingException", e);
            throw new RuntimeException(e);
        }
    }
}
