package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.itinerary.RoutePath;
import banghak.home.halley.domain.itinerary.TransitLeg;

import java.util.List;

/** 한 구간(앞 지점 → 다음 매물)의 안내. */
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

 /** 한 가지 색으로 그릴 구간. */
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
