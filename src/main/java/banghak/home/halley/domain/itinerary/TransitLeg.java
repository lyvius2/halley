package banghak.home.halley.domain.itinerary;

/**
 * 대중교통 한 구간 (설계 I176).
 *
 * <p>ODsay 응답의 `subPath` 하나에 해당합니다. "145번 버스 23분", "2호선 신림→강남 17분"
 * 같은 <b>사람이 읽는 문장</b>을 만들 재료입니다.
 *
 * @param kind         `SUBWAY` · `BUS` · `WALK`
 * @param lineName     노선 이름. 지하철은 `수도권 2호선`, 버스는 `145`. 도보는 null
 * @param from         타는 곳. 도보는 null
 * @param to           내리는 곳. 도보는 null
 * @param minutes      이 구간에 걸리는 시간
 * @param stationCount 정거장 수. 도보는 null
 */
public record TransitLeg(
        Kind kind,
        String lineName,
        String from,
        String to,
        Integer minutes,
        Integer stationCount
) {

    public enum Kind {
        SUBWAY, BUS, WALK
    }

    /**
     * ODsay의 `trafficType`.
     *
     * <p><b>모르는 값은 도보로 봅니다.</b> 새 수단이 생겼을 때 화면이 터지는 것보다
     * "걸어서 몇 분"으로 보이는 편이 낫습니다.
     */
    public static Kind kindOf(int trafficType) {
        return switch (trafficType) {
            case 1 -> Kind.SUBWAY;
            case 2 -> Kind.BUS;
            default -> Kind.WALK;
        };
    }
}
