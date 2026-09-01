package banghak.home.halley.adapter.outbound.external.kakao;

import banghak.home.halley.domain.itinerary.DriveRoute;
import banghak.home.halley.domain.itinerary.RoutePath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카카오 주행 경로선 파싱 (설계 I177).
 *
 * <p><b>`vertexes` 는 경도·위도가 번갈아 든 평평한 배열입니다.</b> 순서를 뒤집으면
 * 서울 대신 소말리아 앞바다에 선이 그려지는데, <b>예외가 나지 않아</b> 화면을 봐야만 압니다.
 */
@DisplayName("카카오 경로선 파싱 (설계 I177 · I195)")
class KakaoRoutePathTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KakaoDirectionsAdapter adapter =
            new KakaoDirectionsAdapter(null, "key", objectMapper);

    /** 도로 둘, 정체 상태가 다르다 — 색이 갈리는 자리다. */
    private static final String TWO_ROADS = """
            {"routes":[{"summary":{"duration":1980,"distance":11125},
             "sections":[{"roads":[
               {"name":"신림로","traffic_state":4,
                "vertexes":[126.929713,37.484191,126.929812,37.484254]},
               {"name":"남부순환로","traffic_state":1,
                "vertexes":[127.020000,37.490000,127.027618,37.497949]}]}]}]}
            """;

    @Test
    @DisplayName("카카오 vertexes를 경도·위도 순서로 읽는다")
    void readsKakaoVertexes() {
        final DriveRoute route = adapter.parse(TWO_ROADS);

        assertThat(route.durationMinutes()).isEqualTo(33);
        // 서울 안이어야 한다 — 뒤집히면 위도 126, 경도 37 이 되어 소말리아 앞바다다
        final RoutePath.Point first = route.path().segments().getFirst().points().getFirst();
        assertThat(first.lat()).isBetween(37.0, 38.0);
        assertThat(first.lng()).isBetween(126.0, 128.0);
    }

    @Test
    @DisplayName("도로마다 구간이 갈리고 정체 상태를 달고 온다 — 이어 붙이면 그 값이 사라진다")
    void splitsSegmentsPerRoad() {
        final DriveRoute route = adapter.parse(TWO_ROADS);

        assertThat(route.path().segments())
                .extracting(RoutePath.Segment::style)
                .containsExactly("TRAFFIC_4", "TRAFFIC_1");
    }

    @Test
    @DisplayName("도로 사이를 잇는다 — 안 이으면 지도에서 선이 끊겨 보인다")
    void bridgesTheGapBetweenRoads() {
        final DriveRoute route = adapter.parse(TWO_ROADS);

        final RoutePath.Point tailOfFirst = route.path().segments().getFirst().points().getLast();
        final RoutePath.Point headOfSecond = route.path().segments().get(1).points().getFirst();
        assertThat(headOfSecond).isEqualTo(tailOfFirst);
        // 이어 붙인 점 하나가 늘었을 뿐, 원래 좌표는 그대로다
        assertThat(route.path().segments().get(1).points()).hasSize(3);
    }

    @Test
    @DisplayName("점이 하나뿐인 도로는 버린다 — 선이 되지 않는다")
    void dropsSinglePointRoad() {
        final DriveRoute route = adapter.parse("""
                {"routes":[{"summary":{"duration":600,"distance":3000},
                 "sections":[{"roads":[
                   {"name":"골목","traffic_state":0,"vertexes":[126.9,37.4]}]}]}]}
                """);

        assertThat(route.path().isEmpty()).isTrue();
        assertThat(route.durationMinutes()).isEqualTo(10);
    }

    @Test
    @DisplayName("경로선이 없어도 소요 시간은 살아 있다 — 하나가 없다고 다른 걸 버리지 않는다")
    void missingPathKeepsDuration() {
        final DriveRoute route = adapter.parse("""
                {"routes":[{"summary":{"duration":600,"distance":3000}}]}
                """);

        assertThat(route.durationMinutes()).isEqualTo(10);
        assertThat(route.path().isEmpty()).isTrue();
    }
}
