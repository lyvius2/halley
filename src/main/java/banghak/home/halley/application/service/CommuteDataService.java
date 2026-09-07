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

 /** path_summary.source 에 남기는 값. */
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
        final Map<Long, Integer> justFetched = prewarm(property, activeUsers);

        final Map<Long, Integer> minutes = new LinkedHashMap<>();
        for (final User user : activeUsers) {
            final Integer userMinutes = justFetched.containsKey(user.id())
                    ? justFetched.get(user.id())
                    : ensureForUser(property, user);
            if (userMinutes != null) {
                minutes.put(user.id(), userMinutes);
            }
        }
        return minutes;
    }

 /** 아직 없는 사람들 몫을 한 번에 받아 둔다. */
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
            log.warn("Batch commute lookup failed - falling back to one at a time. propertyId={}, cause={}",
                    property.id(), e.getMessage());
        }
        return fetched;
    }

 /** 이 값이 어디서 왔는가. */
    private JsonNode sourceOf(TransitResult transit) {
        return objectMapper.createObjectNode()
                .put("source", transit.estimated() ? ESTIMATED_SOURCE : "ODSAY");
    }

 /** 저장된 값이 추정인가. */
    private boolean isEstimate(CommuteResult result) {
        final JsonNode summary = result.pathSummary();
        return summary != null && ESTIMATED_SOURCE.equals(summary.path("source").asString(null));
    }

 /** 못 구한 이유를 반드시 남깁니다. 예전에는 조용히 null을 */
    private Integer ensureForUser(Property property, User user) {
        if (user.workplaceLat() == null || user.workplaceLng() == null) {
            log.info("No commute for user - workplace not set. propertyId={}, userId={}",
                    property.id(), user.id());
            return null;
        }
        final Optional<CommuteResult> cached = commuteResultRepository.findById(property.id(), user.id());
        final boolean cachedIsEstimate = cached.isPresent() && isEstimate(cached.get());
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
