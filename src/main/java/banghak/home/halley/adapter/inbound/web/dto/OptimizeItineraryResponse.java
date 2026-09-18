package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

public record OptimizeItineraryResponse(
        List<Long> orderedPropertyIds,
        int totalMinutes,
        List<ItineraryLegResponse> legs,
        int unknownLegs,
        Status status,
        /** {@code UNAVAILABLE} 일 때 사람에게 보여 줄 말. 아니면 null  */
        String message
) {

    public enum Status {
        OK,
        /** 바깥 API 가 다 막혔다. 오늘은 못 낸다  */
        UNAVAILABLE
    }

    /** 사람이 읽을 말은 한 곳에서만 만든다 — 화면과 서버가 갈리면 안 된다.  */
    public static final String UNAVAILABLE_MESSAGE =
            "현재 서비스 부하로 최적 경로 산출이 어렵습니다. 내일 다시 시도해주세요.";

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
