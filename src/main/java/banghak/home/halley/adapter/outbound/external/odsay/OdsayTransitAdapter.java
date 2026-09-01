package banghak.home.halley.adapter.outbound.external.odsay;

import banghak.home.halley.application.port.out.external.OdsayTransitPort;
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

/**
 * ODsay 대중교통 경로.
 *
 * <p><b>거절 사유를 반드시 남깁니다</b> (설계 I141). ODsay는 실패해도 HTTP 200에
 * {@code error} 객체를 실어 보내므로 <b>폴백이 뜨지 않습니다.</b> 예전에는 그것을 버리고
 * '미산출'로만 돌려줘서, 화면에도 로그에도 <b>왜 안 나왔는지가 없었습니다</b> —
 * 키가 막힌 것인지, 너무 가까운 것인지, 정말 경로가 없는 것인지 구분이 안 됐습니다.
 */
@Slf4j
@Component
public class OdsayTransitAdapter implements OdsayTransitPort {

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

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
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

    /** ODsay는 {@code error}를 객체로도 배열로도 보냅니다 — 엔드포인트마다 다릅니다. */
    private JsonNode errorNode(JsonNode root) {
        final JsonNode error = root.path("error");
        if (error.isArray() && !error.isEmpty()) {
            return error.path(0);
        }
        return error.isObject() ? error : null;
    }

    /**
     * ODsay가 설명을 어느 이름으로 담는지 <b>확실하지 않습니다.</b>
     *
     * <p>`msg`로 읽었더니 운영 로그에 {@code msg=?}만 남았습니다 — 이름이 틀렸는데
     * <b>틀린 줄도 모르고 설명을 또 버렸습니다.</b> 알려진 이름을 차례로 보고,
     * 그래도 없으면 <b>error 노드를 통째로 남깁니다.</b>
     *
     * <p>모르는 모양일수록 통째로 남기는 편이 낫습니다. 골라 담으려다 놓치면
     * 다음 배포를 기다려야 합니다.
     */
    private String messageOf(JsonNode error) {
        for (final String field : new String[]{"message", "msg", "errorMessage", "desc"}) {
            final String value = error.path(field).asString(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return error.toString();
    }

    /**
     * 자주 보는 코드에 사람이 읽을 설명을 붙인다.
     *
     * <p>코드만 남기면 결국 문서를 다시 뒤지게 됩니다. <b>로그를 보는 순간 무엇을 해야 하는지</b>
     * 알 수 있어야 합니다.
     */
    private String hintFor(String code) {
        return switch (code) {
            case "-8" -> "출발지와 도착지가 너무 가깝다 (도보권)";
            // 500은 뭉뚱그린 코드다. ApiKeyAuthFailed 도 500으로 온다 (설계 I141)
            case "500" -> "msg 를 봐야 갈린다 — ApiKeyAuthFailed 면 키·허용 IP 문제다";
            case "3" -> "일일 사용량 초과";
            case "4" -> "서비스 권한 없음 — 키에 이 API가 열려 있는지 확인";
            case "-9" -> "좌표 형식 오류 (X=경도, Y=위도 순서 확인)";
            case "2" -> "인증키 오류 — 등록된 도메인·IP가 맞는지 확인";
            default -> "ODsay 오류 코드표 확인 필요";
        };
    }

    /**
     * 경로선 (설계 I177).
     *
     * <p>`lane[].section[].graphPos` 에 좌표가 들어 있습니다 — <b>`x` 가 경도, `y` 가 위도</b>입니다.
     * 뒤집으면 지도에 아프리카 앞바다가 그려집니다.
     */
    @Override
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
            log.warn("ODsay rejected the lane request. mapObj={}", mapObj);
            return RoutePath.empty();
        }
        final List<RoutePath.Point> points = new ArrayList<>();
        for (final JsonNode lane : root.path("result").path("lane")) {
            for (final JsonNode section : lane.path("section")) {
                for (final JsonNode pos : section.path("graphPos")) {
                    points.add(new RoutePath.Point(pos.path("y").asDouble(), pos.path("x").asDouble()));
                }
            }
        }
        return new RoutePath(points);
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new TransitSearchFailedException("ODsay 응답 파싱에 실패했습니다");
        }
    }
}
