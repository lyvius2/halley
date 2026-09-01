package banghak.home.halley.domain.itinerary;

import banghak.home.halley.domain.scoring.TransitResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ODsay 구간 상세 파싱 (설계 I176).
 *
 * <p>아래 JSON 은 <b>실제 응답에서 가져온 모양</b>입니다 (신림 → 강남, 마들 → 정자).
 */
@DisplayName("대중교통 구간 상세 (설계 I176)")
class TransitLegParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("지하철 구간에서 노선·역·시간·정거장 수를 읽는다")
    void readsSubwayLeg() {
        final TransitResult result = TransitResult.mapResult(objectMapper.readTree("""
                {"result":{"path":[{"info":{"totalTime":19,"subwayTransitCount":1,
                  "busTransitCount":0,"totalWalkTime":2,"mapObj":"2:2:230:222"},
                 "subPath":[
                   {"trafficType":3,"sectionTime":1,"distance":10},
                   {"trafficType":1,"sectionTime":17,"stationCount":8,
                    "startName":"신림","endName":"강남","lane":[{"name":"수도권 2호선"}]},
                   {"trafficType":3,"sectionTime":1,"distance":6}]}]}}
                """));

        assertThat(result.totalMinutes()).isEqualTo(19);
        assertThat(result.mapObj()).isEqualTo("2:2:230:222");
        assertThat(result.legs()).hasSize(3);

        final TransitLeg subway = result.legs().get(1);
        assertThat(subway.kind()).isEqualTo(TransitLeg.Kind.SUBWAY);
        assertThat(subway.lineName()).isEqualTo("수도권 2호선");
        assertThat(subway.from()).isEqualTo("신림");
        assertThat(subway.to()).isEqualTo("강남");
        assertThat(subway.minutes()).isEqualTo(17);
        assertThat(subway.stationCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("버스는 busNo를 노선 이름으로 쓴다 — 지하철과 필드가 다르다")
    void readsBusLeg() {
        final TransitResult result = TransitResult.mapResult(objectMapper.readTree("""
                {"result":{"path":[{"info":{"totalTime":23},
                 "subPath":[{"trafficType":2,"sectionTime":23,"stationCount":12,
                   "startName":"신림역","endName":"강남역","lane":[{"busNo":"145"}]}]}]}}
                """));

        final TransitLeg bus = result.legs().getFirst();
        assertThat(bus.kind()).isEqualTo(TransitLeg.Kind.BUS);
        assertThat(bus.lineName()).isEqualTo("145");
    }

    /**
     * ODsay 는 <b>환승 통로도 도보 구간</b>으로 줍니다. "걸어서 0분"을 줄줄이 늘어놓으면
     * 정작 몇 호선을 타는지가 안 보입니다.
     */
    @Test
    @DisplayName("0분짜리 도보는 버린다 — 환승 통로까지 늘어놓으면 노선이 안 보인다")
    void dropsZeroMinuteWalks() {
        final TransitResult result = TransitResult.mapResult(objectMapper.readTree("""
                {"result":{"path":[{"info":{"totalTime":34},
                 "subPath":[
                   {"trafficType":3,"sectionTime":0},
                   {"trafficType":1,"sectionTime":34,"stationCount":18,
                    "startName":"마들","endName":"강남구청","lane":[{"name":"수도권 7호선"}]},
                   {"trafficType":3,"sectionTime":0}]}]}}
                """));

        assertThat(result.legs()).hasSize(1);
        assertThat(result.legs().getFirst().kind()).isEqualTo(TransitLeg.Kind.SUBWAY);
    }

    @Test
    @DisplayName("모르는 교통수단은 도보로 본다 — 새 수단에 화면이 터지지 않는다")
    void unknownTrafficTypeBecomesWalk() {
        assertThat(TransitLeg.kindOf(99)).isEqualTo(TransitLeg.Kind.WALK);
    }

    @Test
    @DisplayName("subPath가 없으면 구간이 비어 있을 뿐 총 시간은 그대로다")
    void missingSubPathKeepsTotal() {
        final TransitResult result = TransitResult.mapResult(objectMapper.readTree("""
                {"result":{"path":[{"info":{"totalTime":31,"totalWalkTime":5}}]}}
                """));

        assertThat(result.totalMinutes()).isEqualTo(31);
        assertThat(result.legs()).isEmpty();
    }
}
