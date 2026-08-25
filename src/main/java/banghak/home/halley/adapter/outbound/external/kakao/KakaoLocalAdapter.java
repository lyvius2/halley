package banghak.home.halley.adapter.outbound.external.kakao;

import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.config.exception.GeoSearchFailedException;
import banghak.home.halley.config.exception.KakaoApiKeyMissingException;
import banghak.home.halley.domain.geo.GeoSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class KakaoLocalAdapter implements KakaoLocalPort {

    private static final String BASE_URL = "https://dapi.kakao.com";

    private final String restKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public KakaoLocalAdapter(@Value("${kakao.rest-key:}") String restKey,
                             ObjectMapper objectMapper) {
        this.restKey = restKey;
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<GeoSearchResult> searchAddress(String query) {
        if (restKey == null || restKey.isBlank()) {
            throw new KakaoApiKeyMissingException();
        }
        try {
            final String json = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/address.json")
                            .queryParam("query", query)
                            .build())
                    .header("Authorization", "KakaoAK " + restKey)
                    .retrieve()
                    .body(String.class);
            return mapDocuments(objectMapper.readTree(json));
        } catch (RestClientException e) {
            throw new GeoSearchFailedException(e.getMessage());
        } catch (JacksonException e) {
            throw new GeoSearchFailedException("지오코딩 응답 파싱에 실패했습니다");
        }
    }

    List<GeoSearchResult> mapDocuments(JsonNode root) {
        final List<GeoSearchResult> results = new ArrayList<>();
        for (final JsonNode document : root.path("documents")) {
            final String y = document.path("y").asString(null);
            final String x = document.path("x").asString(null);
            results.add(new GeoSearchResult(
                    document.path("address_name").asString(null),
                    document.path("road_address_name").asString(null),
                    y == null ? null : new BigDecimal(y),
                    x == null ? null : new BigDecimal(x)));
        }
        return results;
    }
}
