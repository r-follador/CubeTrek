package com.cubetrek.upload.suuntocloud;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import com.cubetrek.upload.StorageService;
import com.cubetrek.upload.UploadResponse;
import com.cubetrek.upload.polaraccesslink.PolarAccesslinkService;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


@Component
public class SuuntoNewFileEventListener implements ApplicationListener<SuuntoNewFileEventListener.OnEvent> {

    Logger logger = LoggerFactory.getLogger(SuuntoNewFileEventListener.class);

    @Value("${suunto.client.id}")
    String suuntoConsumerKey;
    @Value("${suunto.client.secret}")
    String suuntoConsumerSecret;

    @Value("${suunto.primary.key}")
    String suuntoPrimaryKey;

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;
    @Autowired
    private StorageService storageService;

    @Async
    @Override
    public void onApplicationEvent(OnEvent event) {
        this.downloadFile(event);
    }

    static String fileReqUrl = "https://cloudapi.suunto.com/v2/workout/exportFit/";

    public void downloadFile(OnEvent event) {

        UserThirdpartyConnect userThirdpartyConnect = userThirdpartyConnectRepository.findBySuuntoUserid(event.getSuuntoUser());
        if (userThirdpartyConnect == null){
            logger.error("Suunto: pull file failed: Unknown User id: "+event.getSuuntoUser()+"; Workout id: "+event.getSuuntoWorkoutid());
            return;
        }
        Users user = userThirdpartyConnect.getUser();


        UploadResponse uploadResponse = null;
        try {
            HttpResponse<InputStream> response = userTokenAuthenticationGET_getFile(event.getSuuntoWorkoutid(), userThirdpartyConnect.getSuuntoUseraccesstoken());
            if (response.statusCode() == 200) {
                byte[] filedata = response.body().readAllBytes();
                response.body().close();
                String filename = "suunto-"+event.getSuuntoWorkoutid()+".FIT";
                uploadResponse = storageService.store(user, filedata, filename);
            } else {
                logger.error("Suunto: pull file failed: User id: "+user.getId()+", User Suunto ID '"+event.getSuuntoUser()+"', Suunto WorkoutId '"+event.getSuuntoWorkoutid()+"'");
                logger.error("Response status code: "+response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            logger.error("Suunto: pull file failed: User id: "+user.getId()+", User Suunto ID '"+event.getSuuntoUser()+"', Suunto WorkoutId '"+event.getSuuntoWorkoutid()+"'");
            logger.error("Suunto", e);
        } catch (ExceptionHandling.FileNotAccepted e) {
            //Already logged in StorageService, do nothing
        }

        if (uploadResponse != null)
            logger.info("Suunto: pull file successful: User id: "+user.getId()+"; Track ID: "+uploadResponse.getTrackID());
        else
            logger.error("Suunto: pull file failed: User id: "+user.getId()+", User Suunto ID '"+event.getSuuntoUser()+"', Suunto WorkoutId '"+event.getSuuntoWorkoutid()+"'");
    }

    @Getter
    @Setter
    static public class OnEvent extends ApplicationEvent {
        String suuntoUser;
        String suuntoWorkoutid;

        public OnEvent(String suuntoUser, String suuntoWorkoutid) {
            super(suuntoUser);
            this.suuntoUser = suuntoUser;
            this.suuntoWorkoutid = suuntoWorkoutid;
        }
    }

    public HttpResponse<InputStream> userTokenAuthenticationGET_getFile(String workoutId, String userAccessToken) throws URISyntaxException, IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer "+userAccessToken)
                .header("Ocp-Apim-Subscription-Key", suuntoPrimaryKey)
                .header("Accept", "*/*")
                .uri(new URI(fileReqUrl+workoutId))
                .version(HttpClient.Version.HTTP_1_1)
                .GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

}
