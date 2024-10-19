package com.cubetrek.upload.corosapi;

import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.Users;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;


@Component
public class CorosHistoricDataRequestedListener implements ApplicationListener<CorosHistoricDataRequestedListener.OnEvent> {

    Logger logger = LoggerFactory.getLogger(CorosHistoricDataRequestedListener.class);

    @Autowired
    ApplicationEventPublisher eventPublisher;

    final String corosBaseURL = "https://open.coros.com/"; //live

    @Async
    @Override
    public void onApplicationEvent(OnEvent event) {
        this.requestHistoricData(event);
    }

    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    public void requestHistoricData(OnEvent event) {
        logger.info("Coros: Requesting Historic Data for user "+event.getUser().getId());

        LocalDate currentDate = LocalDate.now();
        String formattedCurrentDate = currentDate.format(formatter);
        LocalDate date30DaysAgo = currentDate.minusDays(30);
        String formattedDate30DaysAgo = date30DaysAgo.format(formatter);

        final String requestUrl = corosBaseURL+"v2/coros/sport/list?token="+event.getUtc().getCorosAccessToken()
                +"&openId="+event.getUtc().getCorosUserid()
                +"&startDate="+formattedDate30DaysAgo
                +"&endDate="+formattedCurrentDate;

        logger.info("Request Url: "+requestUrl);

        RestTemplate restTemplate = new RestTemplate();

        String response = restTemplate.getForObject(requestUrl, String.class);

        logger.info("response string: "+response);

        ArrayList<String> fitUrls = new ArrayList<>();


        try {
            JsonNode rootNode = (new ObjectMapper()).readTree(response);
            CorospingController.findFitUrls(rootNode, fitUrls);
        } catch (JsonProcessingException e) {
            logger.error("Coros: Failed getting historic data",e);
            return;
        }

        for (String fitUrl : fitUrls) {
            eventPublisher.publishEvent(new CorosNewFileEventListener.OnEvent(event.getUtc().getCorosUserid(), fitUrl));
        }
    }

    @Getter
    @Setter
    static public class OnEvent extends ApplicationEvent {
        Users user;
        UserThirdpartyConnect utc;

        public OnEvent(Users user, UserThirdpartyConnect utc) {
            super(user);
            this.user = user;
            this.utc = utc;
        }
    }


}
