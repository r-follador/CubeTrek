package com.cubetrek.upload.polaraccesslink;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import com.cubetrek.upload.StorageService;
import com.cubetrek.upload.UploadResponse;
import com.cubetrek.upload.garminconnect.OnNewGarminFileEvent;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


@Component
public class PolarNewFileEventListener implements ApplicationListener<OnNewPolarFileEvent> {

    Logger logger = LoggerFactory.getLogger(PolarNewFileEventListener.class);

    @Value("${polar.client.id}")
    String polarConsumerKey;
    @Value("${polar.client.secret}")
    String polarConsumerSecret;

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;
    @Autowired
    private StorageService storageService;

    @Async
    @Override
    public void onApplicationEvent(OnNewPolarFileEvent event) {
        this.downloadFile(event);
    }

    public void downloadFile(OnNewPolarFileEvent event) {

        if (!event.getUrl().startsWith("https://www.polaraccesslink.com")) {
            logger.error("PolarAccesslink: will not download file, wrong URL prefix: "+event.getUrl());
            return;
        }

        UserThirdpartyConnect userThirdpartyConnect = userThirdpartyConnectRepository.findByPolarUserid(event.getUserId());
        if (userThirdpartyConnect == null){
            logger.error("PolarAccesslink: pull file failed: Unknown User id: "+event.getUserId());
            return;
        }
        Users user = userThirdpartyConnect.getUser();

        String downloadUrl = event.getUrl()+"/fit";
        HttpClient httpClient = HttpClient.newHttpClient();
        UploadResponse uploadResponse = null;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .header("Authorization", "Bearer "+userThirdpartyConnect.getPolarUseraccesstoken())
                    .header("Accept", "*/*")
                    .uri(new URI(downloadUrl))
                    .version(HttpClient.Version.HTTP_1_1)
                    .GET()
                    .build();


            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                byte[] filedata = response.body().readAllBytes();
                String filename = "polar-"+event.entityId+".FIT";
                uploadResponse = storageService.store(user, filedata, filename);
            } else {
                logger.error("PolarAccesslink: pull file failed: User id: "+user.getId()+", User Polar ID '"+event.getUserId()+"', CallbackURL '"+event.getUrl()+"'");
                logger.error("Response status code: "+response.statusCode());
            }


        } catch (URISyntaxException | IOException | InterruptedException e) {
            logger.error("PolarAccesslink: pull file failed: User id: "+user.getId()+", User Polar ID '"+event.getUserId()+"', CallbackURL '"+event.getUrl()+"'");
            logger.error("PolarAccesslink", e);
        } catch (ExceptionHandling.FileNotAccepted e) {
            //Already logged in StorageService, do nothing
        }

        if (uploadResponse != null)
            logger.info("PolarAccesslink: pull file successful: User id: "+user.getId()+"; Track ID: "+uploadResponse.getTrackID());
        else
            logger.error("PolarAccesslink: pull file failed: User id: "+user.getId()+", User Polar Id '"+event.userId+"', CallbackURL '"+event.getUrl()+"'");
    }


}
