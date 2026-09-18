package banghak.home.halley.domain.group;

import java.time.Instant;

public record UserGroup(
        Long id,
        String name,
        Long createdBy,
        String slackWebhookUrl,
        Instant createdAt
) {

    public boolean hasWebhook() {
        return slackWebhookUrl != null && !slackWebhookUrl.isBlank();
    }
}
