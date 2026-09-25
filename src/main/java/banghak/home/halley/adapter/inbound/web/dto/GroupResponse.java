package banghak.home.halley.adapter.inbound.web.dto;

import java.time.Instant;

public record GroupResponse(
        Long id,
        String name,
        int memberCount,
        String slackWebhookUrl,
        Instant createdAt
) {
}
