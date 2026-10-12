package com.cubetrek.upload.garminconnect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEvent;

@Controller
public class GarminpingController {

    private static final Logger logger = LoggerFactory.getLogger(GarminpingController.class);

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public GarminpingController(ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/garminconnect")
    public ResponseEntity<Void> uploadFile(@RequestBody String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject()) throw new IllegalArgumentException("Expected a JSON object");
            List<ApplicationEvent> events = new ArrayList<>();
            boolean processed = processActivities(summary(root, "activityFiles"), events);
            processed |= processDeregistrations(summary(root, "deregistrations"), events);
            processed |= processPermissionChanges(summary(root, "userPermissionsChange"), events);
            events.forEach(eventPublisher::publishEvent);
            if (!processed) {
                logger.info("Garmin Connect PING received without a supported notification summary");
            }
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException | JsonProcessingException e) {
            logger.warn("Garmin Connect PING rejected: malformed notification ({})", e.getClass().getSimpleName());
            return ResponseEntity.badRequest().build();
        }
    }

    private boolean processActivities(JsonNode activities, List<ApplicationEvent> events) {
        if (!activities.isArray() || activities.isEmpty()) {
            return false;
        }
        for (JsonNode activity : activities) {
            String activityId = optionalText(activity, "activityId");
            if (activity.path("activityId").isIntegralNumber()) activityId = activity.path("activityId").asText();
            if (activityId == null) {
                activityId = "garmin-" + UUID.randomUUID();
            }
            String callbackUrl = requiredText(activity, "callbackURL");
            String fileType = requiredText(activity, "fileType");
            GarminIdentity identity = requiredIdentity(activity);
            events.add(new GarminNewFileEventListener.OnEvent(
                    activityId, identity.userId(), identity.legacyAccessToken(), callbackUrl, fileType));
        }
        return true;
    }

    private boolean processDeregistrations(JsonNode deregistrations, List<ApplicationEvent> events) {
        if (!deregistrations.isArray() || deregistrations.isEmpty()) {
            return false;
        }
        for (JsonNode deregistration : deregistrations) {
            GarminIdentity identity = requiredIdentity(deregistration);
            events.add(new GarminNewDeregistrationEventListener.OnEvent(
                    identity.userId(), identity.legacyAccessToken(), false, true));
        }
        return true;
    }

    private boolean processPermissionChanges(JsonNode permissionChanges, List<ApplicationEvent> events) {
        if (!permissionChanges.isArray() || permissionChanges.isEmpty()) {
            return false;
        }
        for (JsonNode permissionChange : permissionChanges) {
            GarminIdentity identity = requiredIdentity(permissionChange);
            JsonNode permissions = permissionChange.get("permissions");
            if (permissions == null || permissions.isNull()) {
                throw new IllegalArgumentException("permission change omitted permissions");
            }
            events.add(new GarminNewDeregistrationEventListener.OnEvent(
                    identity.userId(), identity.legacyAccessToken(), hasActivityExportPermission(permissions)));
        }
        return true;
    }

    private static GarminIdentity requiredIdentity(JsonNode node) {
        String userId = optionalText(node, "userId");
        String legacyAccessToken = optionalText(node, "userAccessToken");
        if (userId == null && legacyAccessToken == null) {
            throw new IllegalArgumentException("notification omitted userId or userAccessToken");
        }
        // OAuth 1 notifications can include both identifiers. Keep using the access token until Garmin removes it.
        return legacyAccessToken != null
                ? new GarminIdentity(null, legacyAccessToken)
                : new GarminIdentity(userId, null);
    }

    private static boolean hasActivityExportPermission(JsonNode permissions) {
        if (permissions.isArray()) {
            for (JsonNode permission : permissions) {
                if (!permission.isTextual()) throw new IllegalArgumentException("Invalid permission array");
            }
            for (JsonNode permission : permissions) {
                if ("ACTIVITY_EXPORT".equals(permission.asText())) {
                    return true;
                }
            }
            return false;
        }
        if (!permissions.isTextual()) throw new IllegalArgumentException("Invalid permissions");
        return java.util.Arrays.asList(permissions.asText().split("[,\\s]+" )).contains("ACTIVITY_EXPORT");
    }

    private static JsonNode summary(JsonNode root, String name) {
        JsonNode value = root.path(name);
        if (!value.isMissingNode() && !value.isNull() && !value.isArray()) {
            throw new IllegalArgumentException("Expected a notification array");
        }
        return value;
    }

    private static String requiredText(JsonNode node, String name) {
        String value = optionalText(node, name);
        if (value == null) {
            throw new IllegalArgumentException("notification omitted " + name);
        }
        return value;
    }

    private static String optionalText(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private record GarminIdentity(String userId, String legacyAccessToken) {
    }
}
