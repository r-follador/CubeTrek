package com.cubetrek.upload.polaraccesslink;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class CreatePolarWebhookExe {

    //Create a webhook for Polar Accesslink, needs to be only done once
    //see https://www.polar.com/accesslink-api/?python#webhooks

    public static void main(String[] args) throws URISyntaxException, IOException, InterruptedException {
        String polarClientId="xxx";
        String polarClientSecret="xxxx";

        System.out.println("Run only once: this creates a webhook for pings from Polar Accesslink");
        System.out.println("!! IMPORTANT: Save the 'signature_secret_key' which gets returned and update application.properties");
        System.out.println("--Note: polarClientId and polarClientSecret is not loaded from application.properties, needs to be updated manually!");
        System.out.println("--See https://www.polar.com/accesslink-api/?python#webhooks for infos");
        System.out.println();

        String webhookUrl = "https://www.polaraccesslink.com/v3/webhooks";

        String authorization = polarClientId+":"+polarClientSecret;
        String authorizationBase64 = Base64.getEncoder().encodeToString(authorization.getBytes());


        HttpClient httpClient = HttpClient.newHttpClient();

        HttpRequest request_register = HttpRequest.newBuilder()
                .header("Authorization", "Basic "+ authorizationBase64)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json;charset=UTF-8")
                .uri(new URI(webhookUrl))
                .version(HttpClient.Version.HTTP_1_1)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        { "events": [
                            "EXERCISE"
                          ],
                          "url": "https://cubetrek.com/polarconnect" }
                        """))
                .build();

        HttpResponse<String> response = httpClient.send(request_register, HttpResponse.BodyHandlers.ofString());
        System.out.println("Received JSON: Status: "+response.statusCode()+"; body: "+response.body());

        if (response.statusCode()==201) {

            String signature_secret_key = (new ObjectMapper()).readTree(response.body()).get("data").get("signature_secret_key").asText("ERROR");
            System.out.println("--------- signature_secret_key ------");
            System.out.println(signature_secret_key);
            System.out.println("-------------------------------------");
        } else {
            System.out.println("--- Could not retrieve signature_secret_key ;(");
        }


        System.out.println("");
        System.out.println("");
        System.out.println("- GET existing Webhook");

        HttpRequest request_get = HttpRequest.newBuilder()
                .header("Authorization", "Basic "+ authorizationBase64)
                .header("Accept", "application/json;charset=UTF-8")
                .uri(new URI(webhookUrl))
                .version(HttpClient.Version.HTTP_1_1)
                .GET()
                .build();

        HttpResponse<String> response_get = httpClient.send(request_get, HttpResponse.BodyHandlers.ofString());
        System.out.println("Received JSON: Status: "+response_get.statusCode()+"; body: "+response_get.body());
    }


}
