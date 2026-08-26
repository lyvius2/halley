package banghak.home.halley.adapter.outbound.external.kakao;

import banghak.home.halley.application.port.out.external.KakaoDirectionsPort;
import banghak.home.halley.config.exception.GeoSearchFailedException;
import banghak.home.halley.config.exception.KakaoApiKeyMissingException;
import banghak.home.halley.domain.itinerary.DriveRoute;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
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
            final JsonNode summary = objectMapper.readTree(json).path("routes").path(0).path("summary");
            if (summary.isMissingNode() || summary.isNull()) {
                return DriveRoute.missing();
            }
            final int durationSeconds = summary.path("duration").asInt();
            final int distanceM = summary.path("distance").asInt();
            return new DriveRoute(ceilDiv(durationSeconds, 60), distanceM);
        } catch (JacksonException e) {
            throw new GeoSearchFailedException("카카오 Directions 응답 파싱에 실패했습니다");
        }
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}
