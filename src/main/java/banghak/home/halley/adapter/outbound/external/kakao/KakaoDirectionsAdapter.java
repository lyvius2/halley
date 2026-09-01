package banghak.home.halley.adapter.outbound.external.kakao;

import banghak.home.halley.application.port.out.external.KakaoDirectionsPort;
import banghak.home.halley.config.exception.GeoSearchFailedException;
import banghak.home.halley.config.exception.KakaoApiKeyMissingException;
import banghak.home.halley.domain.itinerary.DriveRoute;
import banghak.home.halley.domain.itinerary.RoutePath;
import banghak.home.halley.domain.itinerary.TransitLeg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

@Component
public class KakaoDirectionsAdapter implements KakaoDirectionsPort {

    /** 이보다 짧은 도로는 안 적는다 — 골목까지 늘어놓으면 큰길이 안 보인다 (설계 I193). */
    private static final int MIN_ROAD_METERS = 300;

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
            return new DriveRoute(ceilDiv(durationSeconds, 60), distanceM, pathOf(route), roadsOf(route));
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
    /**
     * 어느 길로 얼마나 (설계 I193).
     *
     * <p>카카오는 도로를 <b>잘게 쪼개서</b> 줍니다 — 27km 한 경로가 26조각입니다.
     * <b>이름이 같으면 이어 붙입니다</b>: "동부간선도로 0.4km"가 여섯 줄 뜨는 것보다
     * "동부간선도로 2.2km" 한 줄이 읽힙니다.
     *
     * <p><b>짧은 구간은 버립니다.</b> 큰길로 들어가기 전의 골목까지 늘어놓으면
     * 정작 어느 도로를 타는지가 안 보입니다. 이름 없는 조각도 마찬가지입니다.
     */
    private List<TransitLeg> roadsOf(JsonNode route) {
        final Map<String, Integer> merged = new LinkedHashMap<>();
        for (final JsonNode section : route.path("sections")) {
            for (final JsonNode road : section.path("roads")) {
                final String name = road.path("name").asString("");
                if (name.isBlank()) {
                    continue;
                }
                merged.merge(name, road.path("distance").asInt(), Integer::sum);
            }
        }
        return merged.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_ROAD_METERS)
                .map(e -> TransitLeg.road(e.getKey(), null, e.getValue()))
                .toList();
    }

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
