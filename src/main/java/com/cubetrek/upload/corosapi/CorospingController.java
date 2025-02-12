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
import org.springframework.scheduling.annotation.Scheduled;
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

    //Coros sometimes sends the same fitUrl multiple times. Keep track of that
    HashSet<String> fitUrlSet = new HashSet<>();

    //Ping from Coros; this is the webhook
    //Workout Summary Data Push, Chapter 5.3
    @PostMapping(value = "/corosconnect")
    public ResponseEntity corosPing(
                                    @RequestBody String payload, // To capture JSON body parameters
                                    @RequestHeader("client") String client,
                                    @RequestHeader("secret") String secret) { // To capture headers

        logger.info("Coros Ping received: "+payload);
        //logger.info("Client: "+client);
        //logger.info("Secret: "+secret);

        ArrayList<FitUrlandOpenid> fitUrlandOpenids = new ArrayList<>();

        if (!(client.equals(corosClientId) && secret.equals(corosClientSecret))) {
            logger.error("Coros Ping Error: corosClientId or corosClientSecret is incorrect");
            logger.error("Payload {}", payload);
        }

        try {
            JsonNode rootNode = (new ObjectMapper()).readTree(payload);
            findFitUrlsAndOpenid(rootNode, fitUrlandOpenids);

        } catch (JsonProcessingException e) {
            logger.error("Coros: Failed parsing ping payload",e);
            logger.error("Coros: Payload that failed: "+payload);
        }

        if (fitUrlandOpenids.isEmpty()) {
            logger.error("Coros: Failed parsing ping payload; could not find fitUrl and/or openId");
            logger.error("Coros: Payload that failed: "+payload);
        } else {
            //Publish async event; handled by CorosNewFileEventListener
            synchronized (this) {
                for (FitUrlandOpenid fitUrl : fitUrlandOpenids) {
                    if (!fitUrlSet.contains(fitUrl.fitUrl)) {
                        fitUrlSet.add(fitUrl.fitUrl);
                        eventPublisher.publishEvent(new CorosNewFileEventListener.OnEvent(fitUrl.openId, fitUrl.fitUrl));
                    }
                }
            }
        }

        //see chapter 5.3.4
        return ResponseEntity.ok("""
                { "message":"ok", "result":"0000" }
                """);
    }

    private record FitUrlandOpenid(String fitUrl, String openId) {};

    // Recursive method to search for fitUrl in all nodes
    private static void findFitUrlsAndOpenid(JsonNode node, List<FitUrlandOpenid> fitUrls) {
        if (node.has("fitUrl") && node.has("openId")) {
            String fitUrl = node.get("fitUrl").asText();
            String openId = node.get("openId").asText();
            fitUrls.add(new FitUrlandOpenid(fitUrl, openId));
        }

        // Iterate over all the child nodes (if the node is an object or array)
        if (node.isObject() || node.isArray()) {
            Iterator<JsonNode> elements = node.elements();
            while (elements.hasNext()) {
                JsonNode childNode = elements.next();
                findFitUrlsAndOpenid(childNode, fitUrls);
            }
        }
    }

    //Service Status Check, Chapter 5.3
    @GetMapping(value = "/corosconnect/status")
    public ResponseEntity corosStatus() {
        logger.info("Coros: Status Request received");
        return ResponseEntity.ok().build();
    }

    @Scheduled(fixedRate = 900_000) //every 15min
    public void clearFitUrlSet() {
        fitUrlSet.clear();
        logger.info("Cleared fitUrlSet");
    }
}
