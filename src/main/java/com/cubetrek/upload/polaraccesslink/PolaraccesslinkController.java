package com.cubetrek.upload.polaraccesslink;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Controller
public class PolaraccesslinkController {
    Logger logger = LoggerFactory.getLogger(PolaraccesslinkController.class);


    @Value("${polar.client.id}")
    String polarClientId;
    @Value("${polar.client.secret}")
    String polarClientSecret;

    @Value("${cubetrek.address}")
    private String httpAddress;

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;

    @Autowired
    PolarAccesslinkService polarAccesslinkService;

    //Get the Authentications
    final String authorizationEndpointUrl = "https://flow.polar.com/oauth2/authorization?response_type=code&client_id=";
    final String tokenEndpointUrl = "https://polarremote.com/v2/oauth2/token";
    final String registerUserUrl = "https://www.polaraccesslink.com/v3/users";


    @GetMapping(value="/profile/connectPolar-step1")
    public String connectToPolar() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        logger.info("PolarAccesslink User tries to Link Polar Account: User id '"+user.getId()+"'");
        return "redirect:"+authorizationEndpointUrl+polarClientId;
    }

    @GetMapping(value="/profile/connectPolar-step2")
    public String connectToPolar2(@RequestParam(value = "error", required = false) String error, @RequestParam(value = "code", required = false) String code) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();

        //TODO: remove in working system
        logger.info("connectPolar-step2: Error: "+error);
        logger.info("connectPolar-step2: Code: "+code);

        if (error != null && !error.isEmpty()) {
            logger.error("Error linking Polar Accesslink account 1. User id: "+user.getId()+"; Error message: '"+error+"'");
            throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Polar Accesslink account. Error message: '"+error+"'");
        }



        try {
            Map<String, String> data = new HashMap<>();
            data.put("grant_type", "authorization_code");
            data.put("code", code);

            HttpResponse<String> response =  polarAccesslinkService.clientCredentialsAuthenticationPOST_getJSON(data, "application/x-www-form-urlencoded", tokenEndpointUrl);

            //TODO: remove in working system
                logger.info("Received JSON1: Status: "+response.statusCode()+"; body: "+response.body());

            if (response.statusCode()!=200) {
                logger.error("Error linking Polar Accesslink account 2. User id: "+user.getId()+"; "+response.body());
                throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Polar Accesslink account.");
            }

            String access_token = (new ObjectMapper()).readTree(response.body()).get("access_token").asText("blarg");
            String expires_in = (new ObjectMapper()).readTree(response.body()).get("expires_in").asText("blarg");
            String x_user_id = (new ObjectMapper()).readTree(response.body()).get("x_user_id").asText("blarg");

            if (access_token.equals("blarg") || expires_in.equals("blarg") || x_user_id.equals("blarg")) {
                logger.error("Error linking Polar Accesslink account 3. User id: "+user.getId()+"; "+response.body());
                throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Polar Accesslink account.");
            }

            UserThirdpartyConnect userThirdpartyConnect = userThirdpartyConnectRepository.findByUser(user);
            UserThirdpartyConnect other = userThirdpartyConnectRepository.findByPolarUserid(x_user_id);

            if ((other != null && userThirdpartyConnect != null && !Objects.equals(other.getId(), userThirdpartyConnect.getId())) || other != null && userThirdpartyConnect == null) {
                throw new ExceptionHandling.UnnamedException("Failed", "This Polar Account is already linked to another CubeTrek account");
            }


            //Register user
            //https://www.polar.com/accesslink-api/?python#users

            HttpResponse<String> response_register = polarAccesslinkService.userTokenAuthenticationPOST_getJSON("{\"member-id\": \"User_id_"+user.getId()+"\"}", "application/json", registerUserUrl, access_token);

            //TODO: remove in working system
            logger.info("Received JSON2: Status: "+response_register.statusCode()+"; body: "+response_register.body());

            if (response_register.statusCode() == 204) {
                logger.error("Error linking Polar Accesslink account 5 (Register User: No content when user with given userId is not found). User id: "+user.getId()+"; "+response_register.body());
                throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Polar Accesslink account.");
            } else if (response_register.statusCode() == 409) {
                logger.error("Error linking Polar Accesslink account 6 (Register User: User already registered to partner or duplicated member-id). User id: "+user.getId()+"; "+response_register.body());
                throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Polar Accesslink account. Your account is already registered.");
            } else if (!(response_register.statusCode() == 200 || response_register.statusCode() == 201)) {
                logger.error("Error linking Polar Accesslink account 7 (Register User). User id: "+user.getId()+"; Status code: "+response_register.statusCode()+"; body: "+response_register.body());
                throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Polar Accesslink account.");
            }

            if (userThirdpartyConnect==null) {
                UserThirdpartyConnect utc = new UserThirdpartyConnect();
                utc.setPolarEnabled(true);
                utc.setPolarUserid(x_user_id);
                utc.setPolarUseraccesstoken(access_token);
                utc.setUser(user);
                userThirdpartyConnectRepository.save(utc);
            } else {
                userThirdpartyConnect.setPolarEnabled(true);
                userThirdpartyConnect.setPolarUserid(x_user_id);
                userThirdpartyConnect.setPolarUseraccesstoken(access_token);
                userThirdpartyConnectRepository.save(userThirdpartyConnect);
            }

            logger.info("PolarAccesslink User successfully linked Polar Account: User id '"+user.getId()+"'; Polar User id: '"+x_user_id+"'");

            return "redirect:/profile";


        } catch (URISyntaxException | IOException | InterruptedException e) {
            logger.error("Error linking Polar Accesslink account 4. User id: "+user.getId(), e);
            throw new ExceptionHandling.UnnamedException("Failed", "Error Linking Polar Accesslink account.");
        }
    }
}
