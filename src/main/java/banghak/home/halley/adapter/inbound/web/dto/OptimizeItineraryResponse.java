package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

/**
 * @param legs 구간별 안내와 경로선 (설계 I176 · I177).
 *             <b>순서를 정한 뒤에 채웁니다</b> — 행렬을 만들 때 다 받아 두면
 *             12개 매물에 156번을 부르는데, 실제로 쓰는 것은 <b>11개 구간</b>뿐입니다
 */
public record OptimizeItineraryResponse(
        List<Long> orderedPropertyIds,
        int totalMinutes,
        List<ItineraryLegResponse> legs
) {

    public static OptimizeItineraryResponse empty() {
        return new OptimizeItineraryResponse(List.of(), 0, List.of());
    }
}
