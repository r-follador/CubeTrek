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
import java.util.Iterator;
import java.util.List;


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

        ArrayList<String> fitUrls = new ArrayList<>();

        fitUrls.addAll(requestHistoricDataDateRange(event.getUtc().getCorosAccessToken(), event.getUtc().getCorosUserid(), currentDate.minusDays(30), currentDate));
        fitUrls.addAll(requestHistoricDataDateRange(event.getUtc().getCorosAccessToken(), event.getUtc().getCorosUserid(), currentDate.minusDays(60), currentDate.minusDays(31)));
        fitUrls.addAll(requestHistoricDataDateRange(event.getUtc().getCorosAccessToken(), event.getUtc().getCorosUserid(), currentDate.minusDays(90), currentDate.minusDays(61)));

        for (String fitUrl : fitUrls) {
            eventPublisher.publishEvent(new CorosNewFileEventListener.OnEvent(event.getUtc().getCorosUserid(), fitUrl));
        }
    }

    private ArrayList<String> requestHistoricDataDateRange(String corosAccessToken, String corosUserid, LocalDate startDate, LocalDate endDate) {
        final String requestUrl = corosBaseURL+"v2/coros/sport/list?token="+corosAccessToken
                +"&openId="+corosUserid
                +"&startDate="+startDate.format(formatter)
                +"&endDate="+endDate.format(formatter);

        logger.info("Request Url: "+requestUrl);

        RestTemplate restTemplate = new RestTemplate();

        String response = restTemplate.getForObject(requestUrl, String.class);

        logger.info("response string: "+response);

        ArrayList<String> fitUrls = new ArrayList<>();


        try {
            JsonNode rootNode = (new ObjectMapper()).readTree(response);
            findFitUrls(rootNode, fitUrls);
        } catch (JsonProcessingException e) {
            logger.error("Coros: Failed getting historic data",e);
            return null;
        }

        return fitUrls;
    }

    // Recursive method to search for fitUrl in all nodes
    private static void findFitUrls(JsonNode node, List<String> fitUrls) {
        if (node.has("fitUrl")) {
            String fitUrl = node.get("fitUrl").asText();
            fitUrls.add(fitUrl);
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
