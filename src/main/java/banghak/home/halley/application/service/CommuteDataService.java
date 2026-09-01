package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.adapter.outbound.persistence.CommuteResultRepository;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.CommuteResult;
import banghak.home.halley.domain.scoring.TransitResult;
import banghak.home.halley.domain.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class CommuteDataService {

    /** `path_summary.source` 에 남기는 값 (설계 I210). */
    private static final String ESTIMATED_SOURCE = "LLM_ESTIMATE";

    private final OdsayTransitPort odsayTransitPort;
    private final CommuteResultRepository commuteResultRepository;
    private final ObjectMapper objectMapper;

    public CommuteDataService(OdsayTransitPort odsayTransitPort,
                              CommuteResultRepository commuteResultRepository,
                              ObjectMapper objectMapper) {
        this.odsayTransitPort = odsayTransitPort;
        this.commuteResultRepository = commuteResultRepository;
        this.objectMapper = objectMapper;
    }

    public Map<Long, Integer> ensureCommuteMinutes(Property property, List<User> activeUsers) {
        if (property.lat() == null || property.lng() == null) {
            return Map.of();
        }
        final Map<Long, Integer> minutes = new LinkedHashMap<>();
        for (final User user : activeUsers) {
            final Integer userMinutes = ensureForUser(property, user);
            if (userMinutes != null) {
                minutes.put(user.id(), userMinutes);
            }
        }
        return minutes;
    }

    /**
     * 이 값이 어디서 왔는가 (설계 I210).
     *
     * <p><b>`path_summary` 에 남깁니다.</b> 컬럼을 새로 만들지 않은 이유는 이 표가
     * 이미 JSON 칸을 갖고 있고, 여기 들어갈 것이 <b>출처 하나</b>이기 때문입니다.
     */
    private JsonNode sourceOf(TransitResult transit) {
        return objectMapper.createObjectNode()
                .put("source", transit.estimated() ? ESTIMATED_SOURCE : "ODSAY");
    }

    /**
     * 저장된 값이 추정인가 (설계 I210).
     *
     * <p><b>모르면 추정이 아닌 것으로 봅니다.</b> 이 표에는 출처를 남기기 전에 쌓인
     * 행이 있는데, 그것들을 추정으로 보면 <b>전부 다시 조회</b>하게 됩니다.
     */
    private boolean isEstimate(CommuteResult result) {
        final JsonNode summary = result.pathSummary();
        return summary != null && ESTIMATED_SOURCE.equals(summary.path("source").asString(null));
    }

    /**
     * <b>못 구한 이유를 반드시 남깁니다</b> (설계 I119). 예전에는 조용히 {@code null}을
     * 돌려줘서, 화면에 '미산출'만 뜨고 <b>직장 좌표가 없어서인지 조회가 실패해서인지</b>
     * 알 수 없었습니다.
     */
    private Integer ensureForUser(Property property, User user) {
        if (user.workplaceLat() == null || user.workplaceLng() == null) {
            log.info("No commute for user - workplace not set. propertyId={}, userId={}",
                    property.id(), user.id());
            return null;
        }
        final Optional<CommuteResult> cached = commuteResultRepository.findById(property.id(), user.id());
        final boolean cachedIsEstimate = cached.isPresent() && isEstimate(cached.get());
        // ODsay 가 준 값은 다시 물을 이유가 없다. <b>추정값은 다르다</b> (설계 I210) —
        // 할당량은 하루마다 풀리므로 진짜 값으로 갈아 끼울 기회를 남긴다
        if (cached.isPresent() && cached.get().totalMinutes() != null && !cachedIsEstimate) {
            return cached.get().totalMinutes();
        }
        if (!odsayTransitPort.isEnabled()) {
            log.info("No commute for user - ODsay key not configured. propertyId={}, userId={}",
                    property.id(), user.id());
            return null;
        }
        try {
            final TransitResult transit = odsayTransitPort.findTransit(
                    user.workplaceLng().doubleValue(), user.workplaceLat().doubleValue(),
                    property.lng().doubleValue(), property.lat().doubleValue());
            if (!transit.isComputed()) {
                // 경로가 없거나(도서·산간) ODsay가 거절한 경우. 저장하지 않으므로 다음에 다시 시도한다.
                // 다만 <b>전에 받아 둔 추정값이 있으면 그것이라도 씁니다</b> (설계 I210) —
                // 할당량이 아직 안 풀렸는데 LLM 까지 실패한 경우다
                if (cachedIsEstimate && cached.get().totalMinutes() != null) {
                    return cached.get().totalMinutes();
                }
                log.info("No commute for user - transit not computed. propertyId={}, userId={}",
                        property.id(), user.id());
                return null;
            }
            if (transit.estimated()) {
                log.info("Commute is an LLM estimate - ODsay quota is spent. propertyId={}, userId={}, minutes={}",
                        property.id(), user.id(), transit.totalMinutes());
            }
            commuteResultRepository.upsert(new CommuteResult(
                    property.id(), user.id(), transit.totalMinutes(),
                    transit.transferCount(), transit.walkMinutes(), sourceOf(transit), Instant.now()));
            return transit.totalMinutes();
        } catch (RuntimeException e) {
            log.warn("Commute lookup failed. propertyId={}, userId={}, cause={}", property.id(), user.id(), e.getMessage());
            return null;
        }
    }
}
