package banghak.home.halley.domain.itinerary;


public record DriveRoute(
        Integer durationMinutes,
        Integer distanceM,
        RoutePath path,
        java.util.List<TransitLeg> roads
) {

 /** 경로선이 필요 없는 자리를 위한 간편 생성. 행렬은 시간만 씁니다. */
    public DriveRoute(Integer durationMinutes, Integer distanceM) {
        this(durationMinutes, distanceM, RoutePath.empty(), java.util.List.of());
    }

    public DriveRoute(Integer durationMinutes, Integer distanceM, RoutePath path) {
        this(durationMinutes, distanceM, path, java.util.List.of());
    }

    public static DriveRoute missing() {
        return new DriveRoute(null, null, RoutePath.empty(), java.util.List.of());
    }

    public boolean isComputed() {
        return durationMinutes != null;
    }
}
