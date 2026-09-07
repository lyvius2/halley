package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

/** 순서를 정한 뒤에 채웁니다. 행렬을 만들 때 다 받아 두면 */
public record OptimizeItineraryResponse(
        List<Long> orderedPropertyIds,
        int totalMinutes,
        List<ItineraryLegResponse> legs,
 /** 이동시간을 못 받은 구간 수. */
        int unknownLegs,
 /** 이 결과를 결과로 볼 수 있는가. */
        Status status,
 /** UNAVAILABLE 일 때 사람에게 보여 줄 말. 아니면 null */
        String message
) {

    public enum Status {
        OK,
 /** 바깥 API 가 다 막혔다. 오늘은 못 낸다 */
        UNAVAILABLE
    }

 /** 사람이 읽을 말은 한 곳에서만 만든다. 화면과 서버가 갈리면 안 된다. */
    public static final String UNAVAILABLE_MESSAGE =
            "현재 서비스 부하로 최적 경로 산출이 어렵습니다. 내일 다시 시도해주세요.";

 /** 구간을 하나도 못 받았으면 결과가 아니다. */
    public static OptimizeItineraryResponse of(List<Long> order, int totalMinutes,
                                               List<ItineraryLegResponse> legs, int unknownLegs) {
        final boolean nothingComputed = !legs.isEmpty() && unknownLegs == legs.size();
        return new OptimizeItineraryResponse(order, totalMinutes, legs, unknownLegs,
                nothingComputed ? Status.UNAVAILABLE : Status.OK,
                nothingComputed ? UNAVAILABLE_MESSAGE : null);
    }

    public static OptimizeItineraryResponse empty() {
        return new OptimizeItineraryResponse(List.of(), 0, List.of(), 0, Status.OK, null);
    }
}
