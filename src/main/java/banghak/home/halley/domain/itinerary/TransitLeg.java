package banghak.home.halley.domain.itinerary;

/** 대중교통 한 구간. */
public record TransitLeg(
        Kind kind,
        String lineName,
        String from,
        String to,
        Integer minutes,
        Integer stationCount
) {

    public enum Kind {
        SUBWAY, BUS, WALK,
 /** 자가용. 도로 이름과 거리를 담는다. */
        ROAD
    }

 /** 자가용 한 구간. */
    public static TransitLeg road(String name, Integer minutes, Integer distanceM) {
        return new TransitLeg(Kind.ROAD, name, null, null, minutes, distanceM);
    }

 /** ODsay의 trafficType. */
    public static Kind kindOf(int trafficType) {
        return switch (trafficType) {
            case 1 -> Kind.SUBWAY;
            case 2 -> Kind.BUS;
            default -> Kind.WALK;
        };
    }
}
