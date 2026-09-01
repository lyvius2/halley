package banghak.home.halley.adapter.outbound.external;

import banghak.home.halley.adapter.outbound.external.odsay.OdsayTransitAdapter;
import banghak.home.halley.adapter.outbound.external.odsay.OdsayTransitFeignClient;
import banghak.home.halley.domain.itinerary.RoutePath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 경로선 파싱 (설계 I177).
 *
 * <p><b>좌표 순서가 이 기능의 급소입니다.</b> 위도와 경도를 뒤집으면 서울 대신
 * 아프리카 앞바다에 선이 그려집니다 — 그런데 <b>예외가 나지 않아</b> 화면을 봐야만 압니다.
 * 두 API 가 순서를 <b>서로 다르게</b> 줍니다.
 *
 * <pre>
 * 카카오: vertexes = [경도, 위도, 경도, 위도, …]   ← 평평한 배열
 * ODsay:  graphPos = {"x": 경도, "y": 위도}       ← x 가 경도
 * </pre>
 */
@DisplayName("경로선 파싱 (설계 I177)")
class RoutePathParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("ODsay graphPos는 x가 경도, y가 위도다")
    void readsOdsayGraphPos() {
        final OdsayTransitAdapter adapter = new OdsayTransitAdapter(stub("""
                {"result":{"lane":[{"section":[{"graphPos":[
                  {"x":126.929695,"y":37.484228},
                  {"x":127.027618,"y":37.497949}]}]}]}}
                """), "key", objectMapper);

        final RoutePath path = adapter.findLane("2:2:230:222");

        assertThat(path.points()).hasSize(2);
        assertThat(path.points().getFirst().lat()).isEqualTo(37.484228);
        assertThat(path.points().getFirst().lng()).isEqualTo(126.929695);
    }

    @Test
    @DisplayName("mapObj가 없으면 부르지 않는다 — 그것 없이는 받을 수 없다")
    void skipsWithoutMapObj() {
        final OdsayTransitAdapter adapter = new OdsayTransitAdapter(new OdsayTransitFeignClient() {
            @Override
            public String findTransit(String apiKey, double sx, double sy, double ex, double ey) {
                return null;
            }

            @Override
            public String loadLane(String apiKey, String mapObject) {
                throw new AssertionError("mapObj 없이 불렀다");
            }
        }, "key", objectMapper);

        assertThat(adapter.findLane(null).isEmpty()).isTrue();
        assertThat(adapter.findLane("  ").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("ODsay가 거절하면 빈 경로 — 화면은 직선으로 되돌아간다")
    void rejectedLaneIsEmpty() {
        final OdsayTransitAdapter adapter = new OdsayTransitAdapter(stub("""
                {"error":{"code":"500","message":"Not Found Route"}}
                """), "key", objectMapper);

        assertThat(adapter.findLane("2:2:230:222").isEmpty()).isTrue();
    }

    private OdsayTransitFeignClient stub(String laneJson) {
        return new OdsayTransitFeignClient() {
            @Override
            public String findTransit(String apiKey, double sx, double sy, double ex, double ey) {
                return null;
            }

            @Override
            public String loadLane(String apiKey, String mapObject) {
                return laneJson;
            }
        };
    }
}
