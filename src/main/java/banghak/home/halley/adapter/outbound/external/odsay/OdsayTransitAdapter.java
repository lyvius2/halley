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

@Slf4j
@Component
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
            // 폴백이 이미 남겼다
            return TransitResult.missing();
        }
        final JsonNode root = parse(json);
        final JsonNode error = errorNode(root);
        if (error != null) {
            final String code = error.path("code").asString("?");
            log.warn("ODsay rejected the request. code={}, msg={}, hint={}, start=({},{}), end=({},{})",
                    code, messageOf(error), hintFor(code),
                    startX, startY, endX, endY);
            // 하루치를 다 쓴 것과 경로가 없는 것은 다르다.
            // 앞은 다른 길로 가면 되고, 뒤는 어디로 가도 답이 없다
            if (isQuotaExhausted(code)) {
                throw new TransitQuotaExceededException("ODsay 일일 호출 한도를 넘었습니다 (code=" + code + ")");
            }
            return TransitResult.missing();
        }
        final TransitResult result = TransitResult.mapResult(root);
        if (!result.isComputed()) {
            // 오류도 아닌데 경로가 없다 — 응답 모양이 바뀌었을 수 있다
            log.warn("ODsay returned no usable path. start=({},{}), end=({},{}), pathCount={}",
                    startX, startY, endX, endY, root.path("result").path("path").size());
        }
        return result;
    }

    private static boolean isQuotaExhausted(String code) {
        return "429".equals(code) || "3".equals(code);
    }

    /** ODsay는 {@code error}를 객체로도 배열로도 보냅니다 — 엔드포인트마다 다릅니다.  */
    private JsonNode errorNode(JsonNode root) {
        final JsonNode error = root.path("error");
        if (error.isArray() && !error.isEmpty()) {
            return error.path(0);
        }
        return error.isObject() ? error : null;
    }

    private String messageOf(JsonNode error) {
        for (final String field : new String[]{"message", "msg", "errorMessage", "desc"}) {
            final String value = error.path(field).asString(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return error.toString();
    }

    private String hintFor(String code) {
        return switch (code) {
            case "-8" -> "출발지와 도착지가 너무 가깝다 (도보권)";
            // 500은 뭉뚱그린 코드다. ApiKeyAuthFailed 도 500으로 온다
            case "500" -> "msg 를 봐야 갈린다 — ApiKeyAuthFailed 면 키·허용 IP 문제다";
            case "3" -> "일일 사용량 초과";
            case "4" -> "서비스 권한 없음 — 키에 이 API가 열려 있는지 확인";
            case "-9" -> "좌표 형식 오류 (X=경도, Y=위도 순서 확인)";
            case "2" -> "인증키 오류 — 등록된 도메인·IP가 맞는지 확인";
            default -> "ODsay 오류 코드표 확인 필요";
        };
    }

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
            // 경로선은 없어도 됩니다 — 화면이 직선으로 되돌아갑니다.
            // 그래서 할당량 초과여도 예외를 던지지 않습니다. LLM 에게 좌표를 지어내게
            // 하면 있지도 않은 길이 지도에 그려집니다
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
