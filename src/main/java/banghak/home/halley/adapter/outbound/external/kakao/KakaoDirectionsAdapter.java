package banghak.home.halley.adapter.outbound.external.kakao;

import banghak.home.halley.config.exception.GeoSearchFailedException;
import banghak.home.halley.config.exception.KakaoApiKeyMissingException;
import banghak.home.halley.domain.itinerary.DriveRoute;
import banghak.home.halley.domain.itinerary.RoutePath;
import banghak.home.halley.domain.itinerary.TransitLeg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

@Component
/** 포트를 직접 구현하지 않습니다. 담아 두기({@link CachingDirections})가 */
public class KakaoDirectionsAdapter {

 /** 이보다 짧은 도로는 안 적는다. 골목까지 늘어놓으면 큰길이 안 보인다. */
    private static final int MIN_ROAD_METERS = 300;

    private final KakaoDirectionsFeignClient client;
    private final String restKey;
    private final ObjectMapper objectMapper;
    private final DirectionsQuota quota;

    public KakaoDirectionsAdapter(KakaoDirectionsFeignClient client,
                                  @Value("${kakao.rest-key:}") String restKey,
                                  ObjectMapper objectMapper,
                                  DirectionsQuota quota) {
        this.client = client;
        this.restKey = restKey;
        this.objectMapper = objectMapper;
        this.quota = quota;
    }

 /** 카카오가 받는 출발 시각 꼴. */
    private static final DateTimeFormatter DEPART_AT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public DriveRoute findRoute(double fromLng, double fromLat, double toLng, double toLat,
                                LocalDateTime departAt) {
        if (restKey == null || restKey.isBlank()) {
            return DriveRoute.missing();
        }
        if (quota.exhausted()) {
            return DriveRoute.missing();
        }
        final String origin = fromLng + "," + fromLat;
        final String destination = toLng + "," + toLat;
        final String json = departAt == null
                ? client.directions(origin, destination, "RECOMMEND")
                : client.futureDirections(origin, destination, "RECOMMEND", DEPART_AT.format(departAt));
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

 /** 실제 주행 경로선. */
 /** 어느 길로 얼마나. */
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

 /** 실제 주행 경로선을 정체 상태별로 끊어서. */
    private RoutePath pathOf(JsonNode route) {
        final List<RoutePath.Segment> segments = new ArrayList<>();
        RoutePath.Point tail = null;
        for (final JsonNode section : route.path("sections")) {
            for (final JsonNode road : section.path("roads")) {
                final JsonNode vertexes = road.path("vertexes");
                final List<RoutePath.Point> points = new ArrayList<>();
                if (tail != null) {
                    points.add(tail);
                }
                for (int i = 0; i + 1 < vertexes.size(); i += 2) {
                    points.add(new RoutePath.Point(
                            vertexes.path(i + 1).asDouble(), vertexes.path(i).asDouble()));
                }
                if (points.size() < 2) {
                    continue;
                }
                tail = points.getLast();
                segments.add(new RoutePath.Segment(
                        "TRAFFIC_" + road.path("traffic_state").asInt(), points));
            }
        }
        return new RoutePath(segments);
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}
