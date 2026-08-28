package banghak.home.halley.adapter.outbound.external.kakao;

import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.config.exception.GeoSearchFailedException;
import banghak.home.halley.config.exception.KakaoApiKeyMissingException;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.geo.PoiResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class KakaoLocalAdapter implements KakaoLocalPort {

    private final KakaoLocalFeignClient client;
    private final String restKey;
    private final ObjectMapper objectMapper;

    public KakaoLocalAdapter(KakaoLocalFeignClient client,
                             @Value("${kakao.rest-key:}") String restKey,
                             ObjectMapper objectMapper) {
        this.client = client;
        this.restKey = restKey;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<GeoSearchResult> searchAddress(String query) {
        requireKey();
        final String json = client.searchAddress(query);
        if (json == null) {
            return List.of();
        }
        return GeoSearchResult.mapDocuments(parse(json));
    }

    @Override
    public List<PoiResult> searchCategory(String categoryGroupCode, double x, double y, int radius) {
        requireKey();
        final String json = client.searchCategory(categoryGroupCode, String.valueOf(x), String.valueOf(y), radius);
        if (json == null) {
            return List.of();
        }
        return PoiResult.mapPois(parse(json));
    }

    @Override
    public List<PoiResult> searchKeyword(String query, String categoryGroupCode, double x, double y, int radius) {
        requireKey();
        final String json = client.searchKeyword(
                query, categoryGroupCode, String.valueOf(x), String.valueOf(y), radius, "distance");
        if (json == null) {
            return List.of();
        }
        return PoiResult.mapPois(parse(json));
    }

    private void requireKey() {
        if (restKey == null || restKey.isBlank()) {
            throw new KakaoApiKeyMissingException();
        }
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new GeoSearchFailedException("카카오 응답 파싱에 실패했습니다");
        }
    }
}
