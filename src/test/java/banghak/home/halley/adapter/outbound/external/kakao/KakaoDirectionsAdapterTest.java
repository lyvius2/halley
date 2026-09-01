package banghak.home.halley.adapter.outbound.external.kakao;

import banghak.home.halley.domain.itinerary.DriveRoute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoDirectionsAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("카카오 Directions 응답에서 소요시간(분)·거리를 매핑한다")
    void parseSummary() throws Exception {
        // given
        final KakaoDirectionsAdapter adapter = new KakaoDirectionsAdapter(null, "key", objectMapper);
        final String json = """
                {"routes":[{"summary":{"distance":1234,"duration":600,"tollFare":0,"fuelPrice":0},"path":[]}]}
                """;

        // when
        final DriveRoute route = adapter.parse(json);

        // then
        assertThat(route.durationMinutes()).isEqualTo(10);
        assertThat(route.distanceM()).isEqualTo(1234);
    }

    @Test
    @DisplayName("경로가 없으면 MISSING을 반환한다")
    void missingRoute() throws Exception {
        // given
        final KakaoDirectionsAdapter adapter = new KakaoDirectionsAdapter(null, "key", objectMapper);
        final String json = "{\"routes\":[]}";

        // when
        final DriveRoute route = adapter.parse(json);

        // then
        assertThat(route.isComputed()).isFalse();
    }

    @Test
    @DisplayName("REST 키가 없으면 호출하지 않고 MISSING을 반환한다")
    void blankKeyReturnsMissing() {
        // given
        final KakaoDirectionsAdapter adapter = new KakaoDirectionsAdapter(null, "  ", objectMapper);

        // when
        final DriveRoute route = adapter.findRoute(126.9, 37.5, 127.0, 37.5, null);

        // then
        assertThat(route.isComputed()).isFalse();
    }
}
