package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.notification.NotificationEventType;
import banghak.home.halley.domain.notification.NotificationStatus;

import java.time.Instant;

public record NotificationLogResponse(
        Long id,
        NotificationEventType eventType,
        Long propertyId,
        NotificationStatus status,
        Integer retryCount,
        String errorMessage,
        Instant createdAt,
        Instant sentAt
) {
}
