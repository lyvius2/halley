package banghak.home.halley.domain.itinerary;

import java.util.List;

/**
 * 지도에 그릴 실제 경로선 (설계 I177 · I195).
 *
 * <p>처음에는 <b>점 하나의 목록</b>이었습니다. 그러면 경로 전체가 한 색이라
 * <b>어디서 갈아타는지, 어디가 막히는지</b>를 지도에서 알 수 없습니다.
 *
 * <p>이제 <b>구간(segment) 목록</b>입니다. 구간마다 색을 달리 칠합니다 —
 * 지하철은 호선 색, 버스는 종류 색, 자가용은 정체 상태 색.
 *
 * @param segments 비어 있을 수 있습니다 — 그때는 화면이 직선으로 되돌아갑니다
 */
public record RoutePath(List<Segment> segments) {

    /**
     * 한 가지 색으로 그릴 구간.
     *
     * @param style 색을 고를 열쇠. <b>색을 여기서 정하지 않습니다</b> —
     *              색은 화면의 것이고, 서버는 <b>무엇인지</b>만 말합니다
     *              (`SUBWAY_2` · `BUS_3` · `TRAFFIC_1` · `WALK`)
     */
    public record Segment(String style, List<Point> points) {
    }

    public record Point(double lat, double lng) {
    }

    public static RoutePath empty() {
        return new RoutePath(List.of());
    }

    /** 색 구분이 없는 한 덩어리. */
    public static RoutePath single(String style, List<Point> points) {
        return points.isEmpty()
                ? empty()
                : new RoutePath(List.of(new Segment(style, points)));
    }

    public boolean isEmpty() {
        return segments == null || segments.isEmpty();
    }
}
