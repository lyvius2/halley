package banghak.home.halley.domain.itinerary;

public record DriveRoute(
        Integer durationMinutes,
        Integer distanceM
) {

    public static DriveRoute missing() {
        return new DriveRoute(null, null);
    }

    public boolean isComputed() {
        return durationMinutes != null;
    }
}
