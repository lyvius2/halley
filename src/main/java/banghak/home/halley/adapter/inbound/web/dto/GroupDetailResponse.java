package banghak.home.halley.adapter.inbound.web.dto;

import java.time.Instant;
import java.util.List;

/** 그룹 정보 화면이 쓰는 응답. */
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

 /** 열람 전용. 연소득과 기존 대출은 담지 않습니다. 현금은 함께 모으는 돈이라 */
    public record Member(
            Long id,
            String nickname,
            String workplaceName,
            long availableBudget,
            boolean enabled
    ) {
    }
}
