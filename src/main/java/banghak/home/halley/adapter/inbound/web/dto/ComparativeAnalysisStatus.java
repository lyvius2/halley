package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

/** 비교 우위 분석 현황. */
public record ComparativeAnalysisStatus(
        boolean pending,
        boolean analysable,
        int propertyCount,
        int minProperties,
        List<ComparativeAnalysisResponse> rankings
) {
}
