package banghak.home.halley.domain.itinerary;

/**
 * @param path 실제 주행 경로선 (설계 I177). 지도에 그린다 — 없으면 직선으로 되돌아간다
 */
public record DriveRoute(
        Integer durationMinutes,
        Integer distanceM,
        RoutePath path
) {

    /** 경로선이 필요 없는 자리를 위한 간편 생성 — 행렬은 시간만 씁니다. */
    public DriveRoute(Integer durationMinutes, Integer distanceM) {
        this(durationMinutes, distanceM, RoutePath.empty());
    }

    public static DriveRoute missing() {
        return new DriveRoute(null, null, RoutePath.empty());
    }

    public boolean isComputed() {
        return durationMinutes != null;
    }
}
