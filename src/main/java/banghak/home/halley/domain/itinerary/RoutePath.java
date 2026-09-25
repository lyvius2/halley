package banghak.home.halley.domain.itinerary;

import java.util.List;

public record RoutePath(List<Segment> segments) {

    public record Segment(String style, List<Point> points) {
    }

    public record Point(double lat, double lng) {
    }

    public static RoutePath empty() {
        return new RoutePath(List.of());
    }

    /** 색 구분이 없는 한 덩어리.  */
    public static RoutePath single(String style, List<Point> points) {
        return points.isEmpty()
                ? empty()
                : new RoutePath(List.of(new Segment(style, points)));
    }

    public boolean isEmpty() {
        return segments == null || segments.isEmpty();
    }
}
