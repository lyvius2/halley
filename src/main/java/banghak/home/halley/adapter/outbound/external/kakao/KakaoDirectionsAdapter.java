package banghak.home.halley.adapter.outbound.external.kakao;

import banghak.home.halley.application.port.out.external.KakaoDirectionsPort;
import banghak.home.halley.config.exception.GeoSearchFailedException;
import banghak.home.halley.config.exception.KakaoApiKeyMissingException;
import banghak.home.halley.domain.itinerary.DriveRoute;
import banghak.home.halley.domain.itinerary.RoutePath;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

@Component
public class KakaoDirectionsAdapter implements KakaoDirectionsPort {

    private final KakaoDirectionsFeignClient client;
    private final String restKey;
    private final ObjectMapper objectMapper;

    public KakaoDirectionsAdapter(KakaoDirectionsFeignClient client,
                                  @Value("${kakao.rest-key:}") String restKey,
                                  ObjectMapper objectMapper) {
        this.client = client;
        this.restKey = restKey;
        this.objectMapper = objectMapper;
    }

    @Override
    public DriveRoute findRoute(double fromLng, double fromLat, double toLng, double toLat) {
        if (restKey == null || restKey.isBlank()) {
            return DriveRoute.missing();
        }
        final String json = client.directions(fromLng + "," + fromLat, toLng + "," + toLat, "RECOMMEND");
        if (json == null) {
            return DriveRoute.missing();
        }
        return parse(json);
    }

    DriveRoute parse(String json) {
        try {
            final JsonNode route = objectMapper.readTree(json).path("routes").path(0);
            final JsonNode summary = route.path("summary");
            if (summary.isMissingNode() || summary.isNull()) {
                return DriveRoute.missing();
            }
            final int durationSeconds = summary.path("duration").asInt();
            final int distanceM = summary.path("distance").asInt();
            return new DriveRoute(ceilDiv(durationSeconds, 60), distanceM, pathOf(route));
        } catch (JacksonException e) {
            throw new GeoSearchFailedException("카카오 Directions 응답 파싱에 실패했습니다");
        }
    }

    /**
     * 실제 주행 경로선 (설계 I177).
     *
     * <p><b>`vertexes` 는 경도·위도가 번갈아 든 평평한 배열입니다.</b>
     * `[lng, lat, lng, lat, …]` — 둘씩 끊어 읽습니다. 순서를 뒤집으면 지도에
     * <b>아프리카 앞바다</b>에 선이 그려집니다.
     *
     * <p>도로가 여러 개면 이어 붙입니다. 한 경로가 도로 아홉 개로 쪼개져 오기도 합니다.
     */
    private RoutePath pathOf(JsonNode route) {
        final List<RoutePath.Point> points = new ArrayList<>();
        for (final JsonNode section : route.path("sections")) {
            for (final JsonNode road : section.path("roads")) {
                final JsonNode vertexes = road.path("vertexes");
                for (int i = 0; i + 1 < vertexes.size(); i += 2) {
                    points.add(new RoutePath.Point(
                            vertexes.path(i + 1).asDouble(), vertexes.path(i).asDouble()));
                }
            }
        }
        return new RoutePath(points);
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}
