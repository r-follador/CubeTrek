package com.cubetrek.upload.corosapi;

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
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
public class CorospingController {
    Logger logger = LoggerFactory.getLogger(CorospingController.class);

    @Value("${coros.client.id}") //aka application_id
    String corosClientId;
    @Value("${coros.client.secret}") //aka accesstoken
    String corosClientSecret;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    //Ping from Coros; this is the webhook
    //Workout Summary Data Push, Chapter 5.3
    @PostMapping(value = "/corosconnect")
    public ResponseEntity corosPing(
                                    @RequestBody String payload, // To capture JSON body parameters
                                    @RequestHeader("client") String client,
                                    @RequestHeader("secret") String secret) { // To capture headers

        logger.info("Coros Ping received: "+payload);
        logger.info("Client: "+client);
        logger.info("Secret: "+secret);


        ArrayList<String> fitUrls = new ArrayList<>();

        try {
            JsonNode rootNode = (new ObjectMapper()).readTree(payload);
            findFitUrls(rootNode, fitUrls);
        } catch (JsonProcessingException e) {
            logger.error("Coros: Failed parsing ping payload",e);
            logger.error("Coros: Payload that failed: "+payload);
        }

        //see chapter 5.3.4
        return ResponseEntity.ok("""
                { "message":"ok", "result":"0000" }
                """);
    }

    // Recursive method to search for fitUrl in all nodes
    public static void findFitUrls(JsonNode node, List<String> fitUrls) {
        if (node.has("fitUrl")) {
            fitUrls.add(node.get("fitUrl").asText());
        }

        // Iterate over all the child nodes (if the node is an object or array)
        if (node.isObject() || node.isArray()) {
            Iterator<JsonNode> elements = node.elements();
            while (elements.hasNext()) {
                JsonNode childNode = elements.next();
                findFitUrls(childNode, fitUrls);
            }
        }
    }

    //Service Status Check, Chapter 5.3
    @GetMapping(value = "/corosconnect/status")
    public ResponseEntity corosStatus() {
        logger.info("Coros: Status Request received");
        return ResponseEntity.ok().build();
    }
}
