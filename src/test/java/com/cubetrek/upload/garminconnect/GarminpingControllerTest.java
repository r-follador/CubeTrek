package com.cubetrek.upload.garminconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatusCode;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class GarminpingControllerTest {

    @Test
    void malformedBatchPublishesNothingEvenIfFirstNotificationIsValid() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        GarminpingController controller = new GarminpingController(publisher, new ObjectMapper());
        assertEquals(400, controller.uploadFile("""
                {"deregistrations":[{"userId":"valid"},{}]}
                """).getStatusCode().value());
        org.mockito.Mockito.verifyNoInteractions(publisher);
    }

    @Test
    void numericActivityIdAndExactCallbackArePreserved() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        GarminpingController controller = new GarminpingController(publisher, new ObjectMapper());
        assertEquals(200, controller.uploadFile("""
                {"activityFiles":[{"userId":"id","activityId":12345678901,"fileType":"FIT",
                  "callbackURL":"https://apis.garmin.com/file?token=a%2Bb&other=1"}]}
                """).getStatusCode().value());
        var event = ArgumentCaptor.forClass(GarminNewFileEventListener.OnEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertEquals("12345678901", event.getValue().getActivityId());
        assertEquals("https://apis.garmin.com/file?token=a%2Bb&other=1", event.getValue().getCallbackURL());
    }

    @Test
    void permissionNameMustMatchExactly() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        GarminpingController controller = new GarminpingController(publisher, new ObjectMapper());
        assertEquals(200, controller.uploadFile("""
                {"userPermissionsChange":[{"userId":"id","permissions":"NOT_ACTIVITY_EXPORT"}]}
                """).getStatusCode().value());
        var event = ArgumentCaptor.forClass(GarminNewDeregistrationEventListener.OnEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertEquals(false, event.getValue().isEnabled());
        assertEquals(false, event.getValue().isDeregistration());
    }

    @Test
    void invalidSummaryTypeOrPermissionArrayIsRejected() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        GarminpingController controller = new GarminpingController(publisher, new ObjectMapper());
        assertEquals(400, controller.uploadFile("{\"activityFiles\":{}}").getStatusCode().value());
        assertEquals(400, controller.uploadFile("""
                {"userPermissionsChange":[{"userId":"id","permissions":["ACTIVITY_EXPORT",42]}]}
                """).getStatusCode().value());
        org.mockito.Mockito.verifyNoInteractions(publisher);
    }

    @Test
    void acceptsOauth2DeregistrationAndPermissionsPayload() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        GarminpingController controller = new GarminpingController(publisher, new ObjectMapper());

        HttpStatusCode status = controller.uploadFile("""
                {
                  "deregistrations": [{"userId": "garmin-user-1"}],
                  "userPermissionsChange": [{
                    "userId": "garmin-user-2",
                    "summaryId": "summary-1",
                    "permissions": ["HISTORICAL_DATA_EXPORT", "ACTIVITY_EXPORT"],
                    "changeTimeInSeconds": 1613065860
                  }]
                }
                """).getStatusCode();

        assertEquals(200, status.value());
        ArgumentCaptor<ApplicationEvent> events = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(publisher, org.mockito.Mockito.times(2)).publishEvent(events.capture());
        List<ApplicationEvent> publishedEvents = events.getAllValues();
        GarminNewDeregistrationEventListener.OnEvent deregistration =
                (GarminNewDeregistrationEventListener.OnEvent) publishedEvents.get(0);
        GarminNewDeregistrationEventListener.OnEvent permissionChange =
                (GarminNewDeregistrationEventListener.OnEvent) publishedEvents.get(1);
        assertEquals("garmin-user-1", deregistration.getGarminUserid());
        assertEquals(false, deregistration.isEnabled());
        assertEquals("garmin-user-2", permissionChange.getGarminUserid());
        assertEquals(true, permissionChange.isEnabled());
        verifyNoMoreInteractions(publisher);
    }

    @Test
    void acceptsActivityPingIdentifiedByGarminUserId() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        GarminpingController controller = new GarminpingController(publisher, new ObjectMapper());

        HttpStatusCode status = controller.uploadFile("""
                {
                    "activityFiles": [{
                    "userId": "garmin-user-1",
                    "callbackURL": "https://example.test/activity?token=opaque",
                    "fileType": "FIT"
                  }]
                }
                """).getStatusCode();

        assertEquals(200, status.value());
        ArgumentCaptor<GarminNewFileEventListener.OnEvent> event =
                ArgumentCaptor.forClass(GarminNewFileEventListener.OnEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertEquals("garmin-user-1", event.getValue().getGarminUserid());
        assertEquals(true, event.getValue().getActivityId().startsWith("garmin-"));
        verifyNoMoreInteractions(publisher);
    }

    @Test
    void prefersTheOauth1AccessTokenWhenBothIdentifiersArePresent() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        GarminpingController controller = new GarminpingController(publisher, new ObjectMapper());

        HttpStatusCode status = controller.uploadFile("""
                {
                  "activityFiles": [{
                    "userId": "garmin-user-1",
                    "userAccessToken": "oauth1-access-token",
                    "callbackURL": "https://example.test/activity",
                    "fileType": "FIT"
                  }]
                }
                """).getStatusCode();

        assertEquals(200, status.value());
        ArgumentCaptor<GarminNewFileEventListener.OnEvent> event =
                ArgumentCaptor.forClass(GarminNewFileEventListener.OnEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertEquals(null, event.getValue().getGarminUserid());
        assertEquals("oauth1-access-token", event.getValue().getLegacyAccessToken());
        verifyNoMoreInteractions(publisher);
    }
}
