package com.cubetrek.upload.polaraccesslink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;


@Service
public class PolarAccesslinkService {

    Logger logger = LoggerFactory.getLogger(PolarAccesslinkService.class);

    @Value("${polar.client.id}")
    String polarClientId;
    @Value("${polar.client.secret}")
    String polarClientSecret;

    /**
     * POSTs with ClientCredentials
     */
    public HttpResponse<String> clientCredentialsAuthenticationPOST_getJSON(Map<String,String> content, String contentType, String url) throws URISyntaxException, IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();

            //Authorization
            //https://www.polar.com/accesslink-api/?python#authentication

            String authorization = polarClientId+":"+polarClientSecret;
            String authorizationBase64 = Base64.getEncoder().encodeToString(authorization.getBytes());

            HttpRequest request = HttpRequest.newBuilder()
                    .header("Authorization", "Basic "+ authorizationBase64)
                    .header("Content-Type", contentType)
                    .header("Accept", "application/json;charset=UTF-8")
                    .uri(new URI(url))
                    .version(HttpClient.Version.HTTP_1_1)
                    .POST(getFormDataAsString(content))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * POSTs with User token (basically all calls to /v3/user
     */

    public HttpResponse<String> userTokenAuthenticationPOST_getJSON(String content, String contentType, String url, String userAccessToken) throws URISyntaxException, IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer "+userAccessToken)
                .header("Content-Type", contentType)
                .header("Accept", "application/json;charset=UTF-8")
                .uri(new URI(url))
                .version(HttpClient.Version.HTTP_1_1)
                .POST(HttpRequest.BodyPublishers.ofString(content))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<InputStream> userTokenAuthenticationGET_getFile(String url, String userAccessToken) throws URISyntaxException, IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer "+userAccessToken)
                .header("Accept", "*/*")
                .uri(new URI(url))
                .version(HttpClient.Version.HTTP_1_1)
                .GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    public HttpResponse<String> userTokenAuthenticationGET_getJSON(String url, String userAccessToken) throws URISyntaxException, IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer "+userAccessToken)
                .header("Accept", "application/json;charset=UTF-8")
                .uri(new URI(url))
                .version(HttpClient.Version.HTTP_1_1)
                .GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }



    private static HttpRequest.BodyPublisher getFormDataAsString(Map<String, String> formData) {
        StringBuilder formBodyBuilder = new StringBuilder();
        for (Map.Entry<String, String> singleEntry : formData.entrySet()) {
            if (formBodyBuilder.length() > 0) {
                formBodyBuilder.append("&");
            }
            formBodyBuilder.append(URLEncoder.encode(singleEntry.getKey(), StandardCharsets.UTF_8));
            formBodyBuilder.append("=");
            formBodyBuilder.append(URLEncoder.encode(singleEntry.getValue(), StandardCharsets.UTF_8));
        }
        return HttpRequest.BodyPublishers.ofString(formBodyBuilder.toString());
    }
}
