package banghak.home.halley.domain.notification;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record NotificationLog(
        Long id,
        NotificationEventType eventType,
        Long propertyId,
        String channel,
        NotificationStatus status,
        Integer retryCount,
        String errorMessage,
        JsonNode payload,
        Instant createdAt,
        Instant sentAt
) {
}
