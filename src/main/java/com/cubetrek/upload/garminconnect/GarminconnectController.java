package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.Users;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

@Controller
public class GarminconnectController {
    Logger logger = LoggerFactory.getLogger(GarminconnectController.class);

    @Value("${garmin.consumer.key}")
    String garminConsumerKey;
    @Value("${garmin.consumer.secret}")
    String garminConsumerSecret;

    private static String apiUrl = "https://connectapi.garmin.com/oauth-service/oauth/request_token";

    @Autowired
    ApplicationEventPublisher eventPublisher;

    //Ping from Garmin
    @PostMapping(value = "/garminconnect")
    @ResponseStatus(HttpStatus.OK)
    public void uploadFile(@RequestBody String payload) {
        logger.info("GarminConnect Hook called: "+payload);
        try {
            JsonNode activities = (new ObjectMapper()).readTree(payload).get("activityFiles");
            if (activities == null || activities.isNull() || activities.isEmpty()) {
                logger.info("GarminConnect: cannot parse");
                return;
            }
            if (activities.isArray()) {
                for (final JsonNode acitivityNode : activities){
                    String userid = acitivityNode.path("userId").asText("blarg");
                    String userAccessToken = acitivityNode.path("userAccessToken").asText("blarg");
                    String callbackURL = acitivityNode.path("callbackURL").asText("blarg");
                    String fileType = acitivityNode.path("fileType").asText("blarg");

                    if (userid.equals("blarg") || userAccessToken.equals("blarg") || callbackURL.equals("blarg") || fileType.equals("blarg")) {
                        logger.warn("GarminConnect: malformed Ping");
                        return;
                    }
                    eventPublisher.publishEvent(new OnNewGarminFileEvent(userid, userAccessToken, callbackURL, fileType));
                }
            }


        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping(value="/profile/connectGarmin")
    public void connectToGarmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();

        HashMap<String, String> data = new HashMap<>();
        data.put("oauth_nonce", String.valueOf(new Random().nextLong()));
        data.put("oauth_timestamp", String.valueOf(System.currentTimeMillis()));
        data.put("oauth_consumer_key", garminConsumerKey);

        //TODO: continue here, see https://apis.garmin.com/tools/oauthAuthorizeUser

        HashMap<String, String> postdata = addApiSignature(data);

        Request request = new Request.Builder()
                .url(apiUrl)
                .post(makeFormBody(postdata))
                .build();
    }

    private HashMap<String, String> addApiSignature(HashMap<String, String> postData) {
        if (postData.containsKey("ApiSignature"))
            throw new RuntimeException("Cannot add ApiSignature: HashMap already contains key 'ApiSignature'");
        postData.put("ApiSignature", getApiSignature(postData));
        return postData;
    }


    private static okhttp3.RequestBody makeFormBody(final HashMap<String, String> map) {
        FormBody.Builder formBody = new FormBody.Builder();
        for (final HashMap.Entry<String, String> entrySet : map.entrySet()) {
            formBody.add(entrySet.getKey(), entrySet.getValue());
        }
        return formBody.build();
    }


    private String getEmptyApiSignature() {
        return getApiSignature("");
    }

    private String getApiSignature(String encodedData) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(garminConsumerSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(encodedData.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }
        return "";
    }

    private String getApiSignature(HashMap<String, String> postData) {
        String payload = getPayload(postData);
        return getApiSignature(payload);
    }

    private static String getPayload(HashMap<String, String> postData) {
        AtomicReference<String> urlEncoded = new AtomicReference<>("");
        postData.forEach((s, s2) -> {
            String concat = urlEncoded.get().isEmpty() ? "" : "&";
            urlEncoded.set(urlEncoded.get() + concat + s + "=" + URLEncoder.encode(s2, StandardCharsets.UTF_8));
        });

        return urlEncoded.get();
    }


}
