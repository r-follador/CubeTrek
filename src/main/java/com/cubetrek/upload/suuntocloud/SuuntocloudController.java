package com.cubetrek.upload.suuntocloud;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import com.cubetrek.upload.polaraccesslink.PolarAccesslinkService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Controller
public class SuuntocloudController {
    Logger logger = LoggerFactory.getLogger(SuuntocloudController.class);


    @Value("${suunto.client.id}")
    String suuntoClientId;

    @Value("${suunto.primary.key}")
    String suuntoPrimaryKey;

    @Value("${suunto.client.secret}")
    String suuntoClientSecret;

    @Value("${cubetrek.address}")
    private String httpAddress;

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;

    @Autowired
    PolarAccesslinkService polarAccesslinkService;

    //Get the Authentications
    final String authorizationEndpointUrl = "https://cloudapi-oauth.suunto.com/oauth/authorize?response_type=code";
    final String tokenEndpointUrl = "https://cloudapi-oauth.suunto.com/oauth/token";


    @GetMapping(value="/profile/connectSuunto-step1")
    public String connectToSuunto() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        logger.info("Suunto User tries to Link Suunto Account: User id '"+user.getId()+"'");
        return "redirect:"+authorizationEndpointUrl+ "&client_id="+suuntoClientId+"&redirect_uri=https://cubetrek.com/profile/connectSuunto-step2";
    }

    @GetMapping(value="/profile/connectSuunto-step2")
    public String connectToSuunto2(@RequestParam(value = "error", required = false) String error, @RequestParam(value = "code", required = false) String code) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();

        //TODO: remove in working system
        logger.info("connectSuunto-step2: Error: "+error);
        logger.info("connectSuunto-step2: Code: "+code);

        if (error != null && !error.isEmpty()) {
            logger.error("Error linking Suunto Account 1. User id: "+user.getId()+"; Error message: '"+error+"'");
            throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Suunto account. Error message: '"+error+"'");
        }


        try {
            Map<String, String> data = new HashMap<>();
            data.put("code", code);
            data.put("grant_type","authorization_code");
            data.put("redirect_uri","https://cubetrek.com/profile/connectSuunto-step2");

            HttpResponse<String> response =  clientPOST_getJSON(data, "application/x-www-form-urlencoded", tokenEndpointUrl);

            //TODO: remove in working system
            logger.info("Received JSON1: Status: "+response.statusCode()+"; body: "+response.body());

            if (response.statusCode()!=200) {
                logger.error("Error linking Suunto  account 2. User id: "+user.getId()+"; "+response.body());
                throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Suunto account.");
            }

            String access_token = (new ObjectMapper()).readTree(response.body()).get("access_token").asText("blarg");
            String refresh_token = (new ObjectMapper()).readTree(response.body()).get("refresh_token").asText("blarg");
            String expires_in = (new ObjectMapper()).readTree(response.body()).get("expires_in").asText("blarg");
            String user_id = (new ObjectMapper()).readTree(response.body()).get("user").asText("blarg");

            if (access_token.equals("blarg") || expires_in.equals("blarg") || user_id.equals("blarg") || refresh_token.equals("blarg")) {
                logger.error("Error linking Suunto account 3. User id: "+user.getId()+"; "+response.body());
                throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Suunto account.");
            }

            UserThirdpartyConnect userThirdpartyConnect = userThirdpartyConnectRepository.findByUser(user);
            UserThirdpartyConnect other = userThirdpartyConnectRepository.findBySuuntoUserid(user_id);

            if ((other != null && userThirdpartyConnect != null && !Objects.equals(other.getId(), userThirdpartyConnect.getId())) || other != null && userThirdpartyConnect == null) {
                throw new ExceptionHandling.UnnamedException("Failed", "This Suunto Account is already linked to another CubeTrek account");
            }

            if (userThirdpartyConnect==null) {
                UserThirdpartyConnect utc = new UserThirdpartyConnect();
                utc.setSuuntoEnabled(true);
                utc.setSuuntoUserid(user_id);
                utc.setSuuntoUseraccesstoken(access_token);
                utc.setSuuntoRefreshtoken(refresh_token);
                utc.setUser(user);
                userThirdpartyConnectRepository.save(utc);
            } else {
                userThirdpartyConnect.setSuuntoEnabled(true);
                userThirdpartyConnect.setSuuntoUserid(user_id);
                userThirdpartyConnect.setSuuntoUseraccesstoken(access_token);
                userThirdpartyConnect.setSuuntoRefreshtoken(refresh_token);
                userThirdpartyConnectRepository.save(userThirdpartyConnect);
            }

            logger.info("Suunto User successfully linked Suunto Account: User id '"+user.getId()+"'; Suunto User id: '"+user_id+"'");

            return "redirect:/profile";


        } catch (URISyntaxException | IOException | InterruptedException e) {
            logger.error("Error linking Suunto account 4. User id: "+user.getId(), e);
            throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Suunto account.");
        }
    }

    public HttpResponse<String> clientPOST_getJSON(Map<String,String> content, String contentType, String url) throws URISyntaxException, IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();

        String authorization = suuntoClientId+":"+suuntoClientSecret;
        String authorizationBase64 = Base64.getEncoder().encodeToString(authorization.getBytes());

        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Basic "+ authorizationBase64)
                .header("Content-Type", contentType)
                .header("Accept", "application/json;charset=UTF-8")
                .uri(new URI(url))
                .version(HttpClient.Version.HTTP_1_1)
                .POST(PolarAccesslinkService.getFormDataAsString(content))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
