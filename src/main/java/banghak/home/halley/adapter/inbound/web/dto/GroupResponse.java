package banghak.home.halley.adapter.inbound.web.dto;

import java.time.Instant;

/** 비어 있으면 알림이 나가지 않는다 */
public record GroupResponse(
        Long id,
        String name,
        int memberCount,
        String slackWebhookUrl,
        Instant createdAt
) {
}
