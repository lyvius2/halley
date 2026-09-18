package banghak.home.halley.adapter.inbound.web.dto;

import java.time.Instant;
import java.util.List;

public record GroupDetailResponse(
        Long id,
        String name,
        String slackWebhookUrl,
        int memberCount,
        long totalCash,
        int propertyCount,
        List<Member> members,
        Instant createdAt
) {

    public record Member(
            Long id,
            String nickname,
            String workplaceName,
            long availableBudget,
            boolean enabled
    ) {
    }
}
