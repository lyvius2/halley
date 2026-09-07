package banghak.home.halley.adapter.outbound.external.odsay;

import banghak.home.halley.config.exception.TransitQuotaExceededException;
import banghak.home.halley.config.exception.TransitSearchFailedException;
import banghak.home.halley.domain.itinerary.RoutePath;
import banghak.home.halley.domain.scoring.TransitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** ODsay 대중교통 경로. */
@Slf4j
@Component
/** 포트를 직접 구현하지 않습니다. */
public class OdsayTransitAdapter {

    private final OdsayTransitFeignClient client;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public OdsayTransitAdapter(OdsayTransitFeignClient client,
                               @Value("${odsay.api-key:}") String apiKey,
                               ObjectMapper objectMapper) {
        this.client = client;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public TransitResult findTransit(double startX, double startY, double endX, double endY) {
        if (apiKey == null || apiKey.isBlank()) {
            return TransitResult.missing();
        }
        final String json = client.findTransit(apiKey, startX, startY, endX, endY);
        if (json == null) {
            return TransitResult.missing();
        }
        final JsonNode root = parse(json);
        final JsonNode error = errorNode(root);
        if (error != null) {
            final String code = error.path("code").asString("?");
            log.warn("ODsay rejected the request. code={}, msg={}, hint={}, start=({},{}), end=({},{})",
                    code, messageOf(error), hintFor(code),
                    startX, startY, endX, endY);
            if (isQuotaExhausted(code)) {
                throw new TransitQuotaExceededException("ODsay 일일 호출 한도를 넘었습니다 (code=" + code + ")");
            }
            return TransitResult.missing();
        }
        final TransitResult result = TransitResult.mapResult(root);
        if (!result.isComputed()) {
            log.warn("ODsay returned no usable path. start=({},{}), end=({},{}), pathCount={}",
                    startX, startY, endX, endY, root.path("result").path("path").size());
        }
        return result;
    }

 /** 하루치를 다 썼는가. */
    private static boolean isQuotaExhausted(String code) {
        return "429".equals(code) || "3".equals(code);
    }

 /** ODsay는 error를 객체로도 배열로도 보냅니다. 엔드포인트마다 다릅니다. */
    private JsonNode errorNode(JsonNode root) {
        final JsonNode error = root.path("error");
        if (error.isArray() && !error.isEmpty()) {
            return error.path(0);
        }
        return error.isObject() ? error : null;
    }

 /** ODsay가 설명을 어느 이름으로 담는지 확실하지 않습니다. */
    private String messageOf(JsonNode error) {
        for (final String field : new String[]{"message", "msg", "errorMessage", "desc"}) {
            final String value = error.path(field).asString(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return error.toString();
    }

 /** 자주 보는 코드에 사람이 읽을 설명을 붙인다. */
    private String hintFor(String code) {
        return switch (code) {
            case "-8" -> "출발지와 도착지가 너무 가깝다 (도보권)";
            case "500" -> "msg 를 봐야 갈린다 — ApiKeyAuthFailed 면 키·허용 IP 문제다";
            case "3" -> "일일 사용량 초과";
            case "4" -> "서비스 권한 없음 — 키에 이 API가 열려 있는지 확인";
            case "-9" -> "좌표 형식 오류 (X=경도, Y=위도 순서 확인)";
            case "2" -> "인증키 오류 — 등록된 도메인·IP가 맞는지 확인";
            default -> "ODsay 오류 코드표 확인 필요";
        };
    }

 /** 경로선을 교통수단별로 끊어서. */
    public RoutePath findLane(String mapObj) {
        if (apiKey == null || apiKey.isBlank() || mapObj == null || mapObj.isBlank()) {
            return RoutePath.empty();
        }
        final String json = client.loadLane(apiKey, "0:0@" + mapObj);
        if (json == null) {
            return RoutePath.empty();
        }
        final JsonNode root = parse(json);
        if (errorNode(root) != null) {
            log.warn("ODsay rejected the lane request - falling back to straight lines. mapObj={}", mapObj);
            return RoutePath.empty();
        }
        final List<RoutePath.Segment> segments = new ArrayList<>();
        for (final JsonNode lane : root.path("result").path("lane")) {
            final List<RoutePath.Point> points = new ArrayList<>();
            for (final JsonNode section : lane.path("section")) {
                for (final JsonNode pos : section.path("graphPos")) {
                    points.add(new RoutePath.Point(pos.path("y").asDouble(), pos.path("x").asDouble()));
                }
            }
            if (points.size() < 2) {
                continue;
            }
            segments.add(new RoutePath.Segment(styleOf(lane), points));
        }
        return new RoutePath(segments);
    }

 /** 이 lane 이 무엇인가. */
    private static String styleOf(JsonNode lane) {
        final int laneClass = lane.path("class").asInt(-1);
        final int type = lane.path("type").asInt(0);
        return switch (laneClass) {
            case 1 -> "BUS_" + type;
            case 2 -> "SUBWAY_" + type;
            default -> "TRANSIT";
        };
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new TransitSearchFailedException("ODsay 응답 파싱에 실패했습니다");
        }
    }
}
