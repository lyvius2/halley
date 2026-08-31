package banghak.home.halley.adapter.inbound.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * 그룹 정보 화면이 쓰는 응답 (설계 I123).
 *
 * <p>`GroupResponse`와 따로 두는 이유: 관리자 목록(`listAll`)은 그룹이 여러 개라
 * 구성원과 매물 수까지 담으면 <b>그룹 수만큼 조회가 늘어납니다.</b> 화면 하나를 위해
 * 목록 API를 무겁게 만들 이유가 없습니다.
 *
 * @param totalCash     구성원들의 보유 현금 합계. 이 앱은 <b>모아서 집을 사려고</b> 만든 것이라
 *                      개인 금액보다 이 값이 먼저 보여야 합니다
 * @param propertyCount 그룹이 보고 있는 매물 수
 */
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

    /**
     * 열람 전용. <b>연소득과 기존 대출은 담지 않습니다</b> — 현금은 함께 모으는 돈이라
     * 공유할 이유가 있지만, 소득은 그렇지 않습니다.
     */
    public record Member(
            Long id,
            String nickname,
            String workplaceName,
            long availableBudget,
            boolean enabled
    ) {
    }
}
