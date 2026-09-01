package banghak.home.halley.domain.itinerary;

import java.util.List;

/**
 * 지도에 그릴 실제 경로선 (설계 I177).
 *
 * <p>여태 지도에는 <b>매물 사이를 잇는 직선</b>을 그렸습니다. 실제로 그렇게 갈 수는
 * 없으므로 거리감이 왜곡됩니다 — 강 건너편이 가까워 보입니다.
 *
 * @param points 좌표 목록. <b>비어 있을 수 있습니다</b> — 그때는 직선으로 되돌아갑니다
 */
public record RoutePath(List<Point> points) {

    public record Point(double lat, double lng) {
    }

    public static RoutePath empty() {
        return new RoutePath(List.of());
    }

    public boolean isEmpty() {
        return points == null || points.isEmpty();
    }
}
