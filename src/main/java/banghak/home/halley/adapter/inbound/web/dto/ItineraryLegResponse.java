package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.itinerary.RoutePath;
import banghak.home.halley.domain.itinerary.TransitLeg;

import java.util.List;

/**
 * 한 구간(앞 지점 → 다음 매물)의 안내.
 *
 * @param fromPropertyId 출발 매물. null 이면 출발지
 * @param toPropertyId   도착 매물
 * @param minutes        이 구간 소요 시간. null 이면 못 받은 것이다 —
 *                       예전에는 999를 넣어 화면이 "999분"이라고 말했다
 * @param steps          대중교통 상세. 자가용은 비어 있다
 * @param path           지도에 그릴 실제 선, 색이 갈리는 자리마다 끊어서.
 *                       비어 있으면 화면이 직선을 그린다
 */
public record ItineraryLegResponse(
        Long fromPropertyId,
        Long toPropertyId,
        Integer minutes,
        List<Step> steps,
        List<Segment> path
) {

    /** "2호선 신림 → 강남 17분 (8정거장)" 을 만들 재료. */
    public record Step(String kind, String lineName, String from, String to,
                       Integer minutes, Integer stationCount) {

        static Step from(TransitLeg leg) {
            return new Step(leg.kind().name(), leg.lineName(), leg.from(), leg.to(),
                    leg.minutes(), leg.stationCount());
        }
    }

    /**
     * 한 가지 색으로 그릴 구간.
     *
     * @param style `SUBWAY_2` · `BUS_3` · `TRAFFIC_1` — 색은 화면이 고른다
     */
    public record Segment(String style, List<Point> points) {
    }

    public record Point(double lat, double lng) {
    }

    public static ItineraryLegResponse of(Long fromPropertyId, Long toPropertyId, Integer minutes,
                                          List<TransitLeg> legs, RoutePath path) {
        return new ItineraryLegResponse(
                fromPropertyId, toPropertyId, minutes,
                legs == null ? List.of() : legs.stream().map(Step::from).toList(),
                path == null || path.isEmpty()
                        ? List.of()
                        : path.segments().stream()
                        .map(seg -> new Segment(seg.style(), seg.points().stream()
                                .map(p -> new Point(p.lat(), p.lng())).toList()))
                        .toList());
    }
}
