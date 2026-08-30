package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

/**
 * 비교 우위 분석 현황 (설계 I61).
 *
 * @param pending       분석이 진행 중인지 — 이 값이 true일 때만 진행 표시를 띄운다 (설계 I72)
 * @param analysable    지금 실행할 수 있는지 — 매물 수가 최소치를 넘고 LLM이 켜져 있는가
 * @param propertyCount 비교 대상 매물 수 (활성·초안 제외)
 * @param minProperties 실행에 필요한 최소 매물 수
 */
public record ComparativeAnalysisStatus(
        boolean pending,
        boolean analysable,
        int propertyCount,
        int minProperties,
        List<ComparativeAnalysisResponse> rankings
) {
}
