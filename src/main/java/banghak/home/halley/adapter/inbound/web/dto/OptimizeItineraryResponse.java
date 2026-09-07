package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

/**
 * @param legs 구간별 안내와 경로선.
 *             순서를 정한 뒤에 채웁니다 — 행렬을 만들 때 다 받아 두면
 *             12개 매물에 156번을 부르는데, 실제로 쓰는 것은 11개 구간뿐입니다
 */
public record OptimizeItineraryResponse(
        List<Long> orderedPropertyIds,
        int totalMinutes,
        List<ItineraryLegResponse> legs,
        /**
         * 이동시간을 못 받은 구간 수.
         *
         * 0이 아니면 합계는 그만큼 빠진 값입니다. 화면이 그 사실을 말해야
         * 합니다 — 예전에는 못 받은 구간마다 999분을 더해 놓고 「예상 이동시간
         * 합계」라고 불렀습니다.
         */
        int unknownLegs,
        /**
         * 이 결과를 결과로 볼 수 있는가.
         *
         * UNAVAILABLE 이면 구간을 하나도 못 받은 것입니다.
         * 그때는 순서마저 뜻이 없습니다 — 최적화기가 모두 같은 값(못 감)으로
         * 매긴 것이라 아무 근거가 없습니다. 화면은 늘어놓지 말고 말해야 합니다.
         */
        Status status,
        /** UNAVAILABLE 일 때 사람에게 보여 줄 말. 아니면 null */
        String message
) {

    public enum Status {
        OK,
        /** 바깥 API 가 다 막혔다. 오늘은 못 낸다 */
        UNAVAILABLE
    }

    /** 사람이 읽을 말은 한 곳에서만 만든다 — 화면과 서버가 갈리면 안 된다. */
    public static final String UNAVAILABLE_MESSAGE =
            "현재 서비스 부하로 최적 경로 산출이 어렵습니다. 내일 다시 시도해주세요.";

    /**
     * 구간을 하나도 못 받았으면 결과가 아니다.
     *
     * 일부만 못 받은 것과 다릅니다 — 일부라면 나머지는 쓸 만하니 합계에서
     * 빠졌다고 적어 두면 됩니다().
     */
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
