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
        // 새로 물어야 할 사람들을 <b>한 번에</b> 받아 둔다 (설계 I217).
        // 사람마다 따로 부르면 ODsay 는 괜찮지만(50ms) LLM 폴백은 한 사람당 4~5초다
        final Map<Long, Integer> justFetched = prewarm(property, activeUsers);

        final Map<Long, Integer> minutes = new LinkedHashMap<>();
        for (final User user : activeUsers) {
            // <b>방금 받은 것은 다시 묻지 않습니다 (설계 I218).</b> 묶어 받은 값은
            // 추정으로 저장되는데, `ensureForUser` 는 추정을 "다시 물어볼 것"으로
            // 보므로(I210) 그대로 두면 <b>같은 사람을 두 번 묻습니다.</b>
            final Integer userMinutes = justFetched.containsKey(user.id())
                    ? justFetched.get(user.id())
                    : ensureForUser(property, user);
            if (userMinutes != null) {
                minutes.put(user.id(), userMinutes);
            }
        }
        return minutes;
    }

    /**
     * 아직 없는 사람들 몫을 한 번에 받아 둔다 (설계 I217).
     *
     * <p>운영 로그에서 <b>한 사람당 4~5초</b>가 걸렸습니다 — 매물 9개 × 사람 2명이면
     * 채점 한 번에 80초입니다. `findTransitBatch` 는 ODsay 면 그냥 돌고,
     * LLM 이면 <b>한 번에 묶어</b> 묻습니다.
     *
     * <p>받은 것은 바로 저장하고, <b>무엇을 받았는지 돌려줍니다 (설계 I218)</b> —
     * 부르는 쪽이 그 사람은 건너뜁니다. 저장만 하고 넘기면 `ensureForUser` 가
     * 추정값을 보고 <b>다시 묻습니다</b>(I210의 "추정은 다시 물어본다" 규칙 때문에).
     *
     * @return 이번에 받아 낸 사람들의 소요시간. 못 받은 사람은 빠집니다
     */
    private Map<Long, Integer> prewarm(Property property, List<User> users) {
        final Map<String, double[]> pending = new LinkedHashMap<>();
        final Map<String, User> byKey = new LinkedHashMap<>();
        for (final User user : users) {
            if (user.workplaceLat() == null || user.workplaceLng() == null) {
                continue;
            }
            final Optional<CommuteResult> cached = commuteResultRepository.findById(property.id(), user.id());
            if (cached.isPresent() && cached.get().totalMinutes() != null && !isEstimate(cached.get())) {
                continue;
            }
            final String key = String.valueOf(user.id());
            byKey.put(key, user);
            pending.put(key, new double[]{
                    user.workplaceLng().doubleValue(), user.workplaceLat().doubleValue(),
                    property.lng().doubleValue(), property.lat().doubleValue()});
        }
        final Map<Long, Integer> fetched = new LinkedHashMap<>();
        if (pending.size() < 2) {
            // 한 명뿐이면 묶을 것이 없다 — ensureForUser 가 평소대로 부른다
            return fetched;
        }
        try {
            odsayTransitPort.findTransitBatch(pending).forEach((key, transit) -> {
                if (!transit.isComputed()) {
                    return;
                }
                final User user = byKey.get(key);
                commuteResultRepository.upsert(new CommuteResult(
                        property.id(), user.id(), transit.totalMinutes(),
                        transit.transferCount(), transit.walkMinutes(), sourceOf(transit), Instant.now()));
                fetched.put(user.id(), transit.totalMinutes());
            });
        } catch (RuntimeException e) {
            // 묶어 받기가 실패해도 아래에서 한 명씩 다시 시도한다
            log.warn("Batch commute lookup failed - falling back to one at a time. propertyId={}, cause={}",
                    property.id(), e.getMessage());
        }
        return fetched;
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
