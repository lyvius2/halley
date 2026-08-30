package banghak.home.halley.adapter.inbound.web.dto;

import java.time.Instant;

/**
 * @param memberCount      남은 인원. 1이면 내가 나갈 때 그룹과 매물이 사라진다 (설계 I89 · 규칙 4)
 * @param slackWebhookUrl  알림이 나갈 곳 (설계 I96). 그룹 구성원끼리 쓰는 채널이라 그대로 보여 준다.
 *                         비어 있으면 알림이 나가지 않는다
 */
public record GroupResponse(
        Long id,
        String name,
        int memberCount,
        String slackWebhookUrl,
        Instant createdAt
) {
}
