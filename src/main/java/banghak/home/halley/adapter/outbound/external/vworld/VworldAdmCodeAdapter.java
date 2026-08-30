package banghak.home.halley.adapter.outbound.external.vworld;

import banghak.home.halley.application.port.out.external.AdmCodePort;
import banghak.home.halley.domain.geo.AdmArea;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * V-World 행정구역 코드 어댑터 (설계 I78).
 *
 * <p>응답 래퍼가 <b>바깥과 안이 같은 이름</b>입니다 — {@code {"admVOList": {"admVOList": [...]}}}.
 * 이름으로 찾으면 바깥 객체에 걸리므로 <b>배열인 자식</b>을 찾습니다.
 */
@Slf4j
@Component
public class VworldAdmCodeAdapter implements AdmCodePort {

    /** 한 시도의 시군구는 많아야 50곳 남짓이라 한 페이지면 충분하다. */
    private static final int MAX_ROWS = 1000;

    private final VworldAdmCodeFeignClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public VworldAdmCodeAdapter(VworldAdmCodeFeignClient client,
                                ObjectMapper objectMapper,
                                @Value("${vworld.api-key:}") String apiKey) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public List<AdmArea> fetchSido() {
        if (!isEnabled()) {
            // 키가 없어 안 부른 것과 불렀는데 실패한 것은 다른 상황이다
            log.info("Skipping VWorld sido lookup - vworld.api-key not configured.");
            return List.of();
        }
        return parse(client.sido(apiKey, "json", MAX_ROWS, 1), "sido");
    }

    @Override
    public List<AdmArea> fetchSigungu(String sidoCode) {
        if (!isEnabled() || sidoCode == null || sidoCode.isBlank()) {
            return List.of();
        }
        return parse(client.sigungu(apiKey, sidoCode, "json", MAX_ROWS, 1), "sigungu:" + sidoCode);
    }

    List<AdmArea> parse(String body, String what) {
        if (body == null) {
            return List.of();
        }
        try {
            final JsonNode items = firstArrayDescendant(objectMapper.readTree(body));
            if (items == null) {
                log.warn("VWorld adm code response has no list. what={}", what);
                return List.of();
            }
            final List<AdmArea> areas = new ArrayList<>();
            for (final JsonNode item : items) {
                final String code = text(item, "admCode");
                if (code == null) {
                    continue;
                }
                areas.add(new AdmArea(code, text(item, "admCodeNm"), text(item, "lowestAdmCodeNm")));
            }
            return areas;
        } catch (RuntimeException e) {
            log.warn("Failed to parse VWorld adm code response. what={}, cause={}", what, e.toString());
            return List.of();
        }
    }

    /** 바깥·안쪽 래퍼 이름이 같아 이름으로는 못 찾는다. 배열이 나올 때까지 내려간다. */
    private JsonNode firstArrayDescendant(JsonNode node) {
        if (node.isArray()) {
            return node;
        }
        for (final JsonNode child : node) {
            final JsonNode found = firstArrayDescendant(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String text(JsonNode item, String field) {
        final String value = item.path(field).asString(null);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
