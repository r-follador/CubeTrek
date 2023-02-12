package com.cubetrek.upload.suuntocloud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SuuntopingController {
    Logger logger = LoggerFactory.getLogger(SuuntopingController.class);

    @Value("${suunto.client.id}")
    String suuntoConsumerKey;
    @Value("${suunto.client.secret}")
    String suuntoConsumerSecret;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    //Ping from Suunto; this is the webhook
    @PostMapping(value = "/suuntoconnect")
    public ResponseEntity suuntoPing(@RequestParam(value = "username", required = false) String username, @RequestParam(value = "workoutid", required = false) String workoutid) {
        logger.info("SuuntoCloud Ping received: "+username+"; workout id: "+workoutid);

        //TODO: workoutid needs to be workoutKey, othwerise 500 error
        if (username==null || username.isEmpty() || workoutid==null || workoutid.isEmpty()) {
            logger.warn("Suunto Ping: malformed Ping, missing keys; received: Suunto username: "+username+"; workout id: "+workoutid);
            return ResponseEntity.badRequest().build();
        }

        eventPublisher.publishEvent(new SuuntoNewFileEventListener.OnEvent(username, workoutid));
        return ResponseEntity.ok().build();
    }
}
