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

    /** `path_summary.source` 에 남기는 값.  */
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
        // 새로 물어야 할 사람들을 한 번에 받아 둔다.
        // 사람마다 따로 부르면 ODsay 는 괜찮지만(50ms) LLM 폴백은 한 사람당 4~5초다
        final Map<Long, Integer> justFetched = prewarm(property, activeUsers);

        final Map<Long, Integer> minutes = new LinkedHashMap<>();
        for (final User user : activeUsers) {
            // 방금 받은 것은 다시 묻지 않습니다. 묶어 받은 값은
            // 추정으로 저장되는데, `ensureForUser` 는 추정을 "다시 물어볼 것"으로
            // 보므로 그대로 두면 같은 사람을 두 번 묻습니다.
            final Integer userMinutes = justFetched.containsKey(user.id())
                    ? justFetched.get(user.id())
                    : ensureForUser(property, user);
            if (userMinutes != null) {
                minutes.put(user.id(), userMinutes);
            }
        }
        return minutes;
    }

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

    private JsonNode sourceOf(TransitResult transit) {
        return objectMapper.createObjectNode()
                .put("source", transit.estimated() ? ESTIMATED_SOURCE : "ODSAY");
    }

    private boolean isEstimate(CommuteResult result) {
        final JsonNode summary = result.pathSummary();
        return summary != null && ESTIMATED_SOURCE.equals(summary.path("source").asString(null));
    }

    private Integer ensureForUser(Property property, User user) {
        if (user.workplaceLat() == null || user.workplaceLng() == null) {
            log.info("No commute for user - workplace not set. propertyId={}, userId={}",
                    property.id(), user.id());
            return null;
        }
        final Optional<CommuteResult> cached = commuteResultRepository.findById(property.id(), user.id());
        final boolean cachedIsEstimate = cached.isPresent() && isEstimate(cached.get());
        // ODsay 가 준 값은 다시 물을 이유가 없다. 추정값은 다르다 —
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
                // 다만 전에 받아 둔 추정값이 있으면 그것이라도 씁니다 —
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
