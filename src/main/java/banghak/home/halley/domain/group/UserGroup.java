package banghak.home.halley.domain.group;

import java.time.Instant;

/** 회원과 매물이 속하는 단위. */
public record UserGroup(
        Long id,
        String name,
        Long createdBy,
 /** 이 그룹의 알림이 나가는 곳. 비어 있으면 보내지 않습니다. * 전역 웹훅으로 흘려보내면 우리 매물이 남의 채널에 뜹니다. */
        String slackWebhookUrl,
        Instant createdAt
) {

    public boolean hasWebhook() {
        return slackWebhookUrl != null && !slackWebhookUrl.isBlank();
    }
}
