package banghak.home.halley.adapter.outbound.external.vworld;

import banghak.home.halley.application.port.out.external.LandUsePort;
import banghak.home.halley.domain.landuse.LandUse;
import banghak.home.halley.domain.landuse.LandUseConflict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V-World 토지이용계획 어댑터 (설계 I69).
 *
 * <p>공시가격(I54)과 같은 응답 규약입니다 — 인증 실패도 HTTP 200 + 본문 `resultCode`로 오고,
 * 정상 응답의 `resultCode`는 빈 문자열입니다.
 *
 * <p><b>같은 지역·지구가 여러 번 나옵니다.</b> 실측(은마아파트)에서 35건 중 `토지거래계약에관한
 * 허가구역`이 4번, `일반철도`가 5번 나왔습니다. 관리번호가 달라서인데 매수자에게는 같은 말이므로
 * <b>(코드, 이름, 관계) 조합으로 중복을 제거</b>합니다.
 */
@Slf4j
@Component
public class VworldLandUseAdapter implements LandUsePort {

    private static final int MAX_ROWS = 1000;

    private final VworldLandUseFeignClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public VworldLandUseAdapter(VworldLandUseFeignClient client,
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
    public List<LandUse> fetch(String pnu) {
        if (!isEnabled()) {
            log.info("Skipping land use lookup - api key not configured.");
            return List.of();
        }
        if (pnu == null || pnu.length() != 19) {
            log.info("Skipping land use lookup - invalid PNU. pnu={}", pnu);
            return List.of();
        }
        final String body = client.landUse(apiKey, pnu, "json", MAX_ROWS, 1);
        if (body == null) {
            return List.of();
        }
        return parse(body, pnu);
    }

    List<LandUse> parse(String body, String pnu) {
        try {
            final JsonNode wrapper = firstObjectChild(objectMapper.readTree(body));
            final String resultCode = wrapper.path("resultCode").asString(null);
            if (resultCode != null && !resultCode.isBlank()
                    && !"NORMAL_SERVICE".equals(resultCode) && !"00".equals(resultCode)) {
                log.warn("VWorld land use lookup rejected. pnu={}, resultCode={}, resultMsg={}",
                        pnu, resultCode, wrapper.path("resultMsg").asString(null));
                return List.of();
            }
            final JsonNode items = firstArrayChild(wrapper);
            if (items == null) {
                log.info("VWorld land use returned no items. pnu={}, totalCount={}",
                        pnu, wrapper.path("totalCount").asString("?"));
                return List.of();
            }
            final Instant now = Instant.now();
            final Set<String> seen = new LinkedHashSet<>();
            final List<LandUse> result = new ArrayList<>();
            for (final JsonNode item : items) {
                final String name = item.path("prposAreaDstrcCodeNm").asString(null);
                if (name == null || name.isBlank()) {
                    continue;
                }
                final String code = item.path("prposAreaDstrcCode").asString(null);
                final LandUseConflict conflict =
                        LandUseConflict.fromLabel(item.path("cnflcAtNm").asString(null));
                if (!seen.add(code + "|" + name + "|" + conflict)) {
                    continue;
                }
                result.add(new LandUse(null, null, code, name.trim(), conflict, pnu, now));
            }
            log.info("Land use fetched. pnu={}, raw={}, distinct={}", pnu, items.size(), result.size());
            return result;
        } catch (RuntimeException e) {
            log.warn("Failed to parse VWorld land use response. pnu={}, cause={}", pnu, e.getMessage());
            return List.of();
        }
    }

    private JsonNode firstObjectChild(JsonNode root) {
        for (final Map.Entry<String, JsonNode> entry : root.properties()) {
            if (entry.getValue().isObject()) {
                return entry.getValue();
            }
        }
        return root;
    }

    private JsonNode firstArrayChild(JsonNode wrapper) {
        for (final Map.Entry<String, JsonNode> entry : wrapper.properties()) {
            if (entry.getValue().isArray()) {
                return entry.getValue();
            }
            if (entry.getValue().isObject() && !entry.getValue().path("pnu").isMissingNode()) {
                return objectMapper.createArrayNode().add(entry.getValue());
            }
        }
        return null;
    }
}
