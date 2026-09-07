package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryRequest;
import banghak.home.halley.adapter.inbound.web.dto.ItineraryLegResponse;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryResponse;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyVisitRepository;
import banghak.home.halley.application.port.out.cache.TravelTimeCache;
import banghak.home.halley.application.port.out.external.KakaoDirectionsPort;
import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.adapter.inbound.web.dto.ItineraryDraft;
import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.application.port.out.cache.StartLocationCache;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.config.VirtualThreadGate;
import banghak.home.halley.domain.itinerary.StartLocation;
import banghak.home.halley.domain.itinerary.DriveRoute;
import banghak.home.halley.domain.itinerary.ItineraryOptimizer;
import banghak.home.halley.domain.itinerary.PropertyVisit;
import banghak.home.halley.domain.itinerary.TravelCostMatrix;
import banghak.home.halley.domain.itinerary.TravelMode;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.TransitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ItineraryService {

    private static final long DEPOT_ID = -1L;
 /** 출발지 캐시(I52)와 같은 수명. 임장 준비는 며칠에 걸친다. */
    private static final java.time.Duration DRAFT_TTL = java.time.Duration.ofDays(7);
 /** 한 번의 계산 안에서만 쓰는 기억. */
    private final ThreadLocal<Map<String, TransitResult>> transitMemo = ThreadLocal.withInitial(HashMap::new);
 /** 자동차 길도 한 번의 계산 안에서만 기억한다. */
    private final ThreadLocal<Map<String, DriveRoute>> driveMemo = ThreadLocal.withInitial(java.util.concurrent.ConcurrentHashMap::new);
    private static final int UNREACHABLE_MINUTES = 999;

    private final PropertyRepository propertyRepository;
    private final PropertyAccessGuard propertyAccessGuard;
    private final PropertyVisitRepository propertyVisitRepository;
    private final KakaoDirectionsPort kakaoDirectionsPort;
    private final OdsayTransitPort odsayTransitPort;
    private final TravelTimeCache travelTimeCache;
    private final ItineraryOptimizer optimizer;

    private final StartLocationCache startLocationCache;
    private final CachePort cache;
    private final ObjectMapper objectMapper;
 /** 구간들을 한꺼번에 받아 두는 자리. */
    private final VirtualThreadGate gate;
 /** 미리 받아 두기에 쓸 수 있는 시간. */
    private final java.time.Duration prewarmBudget;

    public ItineraryService(PropertyAccessGuard propertyAccessGuard,
                            PropertyRepository propertyRepository,
                            PropertyVisitRepository propertyVisitRepository,
                            KakaoDirectionsPort kakaoDirectionsPort,
                            OdsayTransitPort odsayTransitPort,
                            TravelTimeCache travelTimeCache,
                            ItineraryOptimizer optimizer,
                            StartLocationCache startLocationCache,
                            CachePort cache,
                            ObjectMapper objectMapper,
                            @Qualifier("itineraryGate") VirtualThreadGate gate,
                            @Value("${itinerary.prewarm-budget-seconds:20}") long prewarmBudgetSeconds) {
        this.propertyAccessGuard = propertyAccessGuard;
        this.propertyRepository = propertyRepository;
        this.propertyVisitRepository = propertyVisitRepository;
        this.kakaoDirectionsPort = kakaoDirectionsPort;
        this.odsayTransitPort = odsayTransitPort;
        this.travelTimeCache = travelTimeCache;
        this.optimizer = optimizer;
        this.startLocationCache = startLocationCache;
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.gate = gate;
        this.prewarmBudget = java.time.Duration.ofSeconds(prewarmBudgetSeconds);
    }

 /** 작업 중인 것을 사용자별로 담아 둔다. */
    public ItineraryDraft loadDraft() {
        return cache.get(CachePort.ITINERARY, String.valueOf(currentUserId()))
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, ItineraryDraft.class);
                    } catch (RuntimeException e) {
                        log.warn("Itinerary draft unreadable - starting empty. cause={}", e.getMessage());
                        cache.evict(CachePort.ITINERARY, String.valueOf(currentUserId()));
                        return ItineraryDraft.empty();
                    }
                })
                .orElseGet(ItineraryDraft::empty);
    }

    public void saveDraft(ItineraryDraft draft) {
        cache.put(CachePort.ITINERARY, String.valueOf(currentUserId()),
                objectMapper.writeValueAsString(draft), DRAFT_TTL);
    }

    public void clearDraft() {
        cache.evict(CachePort.ITINERARY, String.valueOf(currentUserId()));
    }

    public OptimizeItineraryResponse optimize(OptimizeItineraryRequest request) {
        transitMemo.get().clear();
        driveMemo.get().clear();
        try {
            final TravelMode mode = modeOf(request.travelMode());
            final List<Property> properties = loadWithCoords(request.propertyIds());
            if (properties.isEmpty()) {
                return OptimizeItineraryResponse.empty();
            }
            final LocalDateTime departAt = departAt(request.visitDate(), request.windowStart());
            if (mode == TravelMode.TRANSIT) {
                prewarmTransit(properties, request.startLat(), request.startLng());
            } else {
                prewarmDriving(properties, request.startLat(), request.startLng(), departAt);
            }
            final TravelCostMatrix matrix =
                    buildMatrix(properties, request.startLat(), request.startLng(), mode, departAt);
            final List<Long> order = optimizer.optimize(DEPOT_ID, properties.stream().map(Property::id).toList(), matrix);
            final List<ItineraryLegResponse> legs = legsOf(order, properties,
                    request.startLat(), request.startLng(), mode, departAt,
                    request.stayMinutes() == null ? 25 : request.stayMinutes());
            final int unknown = (int) legs.stream().filter(leg -> leg.minutes() == null).count();
            if (unknown > 0) {
                log.info("Some legs have no travel time. total={}, unknown={}, mode={}",
                        legs.size(), unknown, mode);
            }
            return OptimizeItineraryResponse.of(order, totalMinutes(legs), legs, unknown);
        } finally {
            transitMemo.remove();
            driveMemo.remove();
        }
    }

 /** 자동차 길을 미리, 한꺼번에 받아 둔다. */
    private void prewarmDriving(List<Property> properties, BigDecimal startLat, BigDecimal startLng,
                                LocalDateTime departAt) {
        final List<double[]> points = new ArrayList<>();
        points.add(new double[]{startLng.doubleValue(), startLat.doubleValue()});
        properties.forEach(p -> points.add(new double[]{p.lng().doubleValue(), p.lat().doubleValue()}));

        final Map<String, DriveRoute> memo = driveMemo.get();
        final List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            for (int j = 1; j < points.size(); j++) {
                if (i == j) {
                    continue;
                }
                final double[] a = points.get(i);
                final double[] b = points.get(j);
                final String key = driveKey(a[0], a[1], b[0], b[1], departAt);
                tasks.add(() -> memo.put(key,
                        kakaoDirectionsPort.findRoute(a[0], a[1], b[0], b[1], departAt)));
            }
        }
        gate.runWithin(tasks, prewarmBudget);
        if (memo.size() < tasks.size()) {
            log.info("Drive legs not fully prewarmed within budget. asked={}, got={}",
                    tasks.size(), memo.size());
        }
    }

 /** 언제 출발하는가. */
    private static LocalDateTime departAt(LocalDate visitDate, LocalTime windowStart) {
        return visitDate == null
                ? null
                : visitDate.atTime(windowStart == null ? LocalTime.of(9, 0) : windowStart);
    }

 /** 정해진 순서를 따라가며 구간 안내와 경로선을 모은다. */
    private List<ItineraryLegResponse> legsOf(List<Long> order, List<Property> properties,
                                              BigDecimal startLat, BigDecimal startLng, TravelMode mode,
                                              LocalDateTime departAt, int stayMinutes) {
        final Map<Long, Property> byId = properties.stream()
                .collect(Collectors.toMap(Property::id, Function.identity()));
        final List<ItineraryLegResponse> legs = new ArrayList<>();
        double fromLat = startLat.doubleValue();
        double fromLng = startLng.doubleValue();
        Long fromId = null;
        for (final Long toId : order) {
            final Property to = byId.get(toId);
            if (to == null) {
                continue;
            }
            final ItineraryLegResponse leg = legOf(fromId, to, fromLng, fromLat, mode, departAt);
            legs.add(leg);
            if (departAt != null && leg.minutes() != null) {
                departAt = departAt.plusMinutes((long) leg.minutes() + stayMinutes);
            }
            fromId = toId;
            fromLat = to.lat().doubleValue();
            fromLng = to.lng().doubleValue();
        }
        return legs;
    }

    private ItineraryLegResponse legOf(Long fromId, Property to, double fromLng, double fromLat,
                                       TravelMode mode, LocalDateTime departAt) {
        final double toLng = to.lng().doubleValue();
        final double toLat = to.lat().doubleValue();
        if (mode == TravelMode.DRIVING) {
            final DriveRoute route = drive(fromLng, fromLat, toLng, toLat, departAt);
            return ItineraryLegResponse.of(fromId, to.id(),
                    route.isComputed() ? route.durationMinutes() : null,
                    route.roads(), route.path());
        }
        final TransitResult remembered = transitMemo.get().get(legKey(fromLng, fromLat, toLng, toLat));
        final TransitResult transit = remembered != null
                ? remembered
                : odsayTransitPort.findTransit(fromLng, fromLat, toLng, toLat);
        return ItineraryLegResponse.of(fromId, to.id(),
                transit.isComputed() ? transit.totalMinutes() : null,
                transit.legs(), odsayTransitPort.findLane(transit.mapObj()));
    }

 /** 가 본 곳. */
    public List<Long> visitedPropertyIds() {
        return propertyVisitRepository.findByUser(currentUserId()).stream()
                .map(PropertyVisit::propertyId)
                .toList();
    }

 /** 방문완료를 켜고 끈다. */
    @Transactional
    public void markVisited(Long propertyId, boolean visited) {
        propertyAccessGuard.require(propertyId);
        if (visited) {
            propertyVisitRepository.mark(propertyId, currentUserId(), Instant.now());
        } else {
            propertyVisitRepository.clear(propertyId, currentUserId());
        }
    }

    private List<Property> loadWithCoords(List<Long> propertyIds) {
        return propertyIds.stream()
                .map(id -> propertyAccessGuard.require(id))
                .filter(p -> p.lat() != null && p.lng() != null)
                .toList();
    }

    private TravelCostMatrix buildMatrix(List<Property> properties, BigDecimal startLat, BigDecimal startLng,
                                         TravelMode mode, LocalDateTime departAt) {
        final Map<Long, Property> byId = properties.stream()
                .collect(Collectors.toMap(Property::id, Function.identity()));
        return (fromId, toId) -> {
            final Property from = fromId == DEPOT_ID ? null : byId.get(fromId);
            final Property to = byId.get(toId);
            if (to == null) {
                return UNREACHABLE_MINUTES;
            }
            if (from == null) {
                return travelTime(startLng.doubleValue(), startLat.doubleValue(),
                        to.lng().doubleValue(), to.lat().doubleValue(), mode, departAt);
            }
            return travelTime(from.lng().doubleValue(), from.lat().doubleValue(),
                    to.lng().doubleValue(), to.lat().doubleValue(), mode, departAt);
        };
    }

 /** 아는 것만 더한다. */
    private static int totalMinutes(List<ItineraryLegResponse> legs) {
        return legs.stream()
                .map(ItineraryLegResponse::minutes)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int travelTime(double fromLng, double fromLat, double toLng, double toLat, TravelMode mode,
                           LocalDateTime departAt) {
        if (mode == TravelMode.DRIVING) {
            final DriveRoute route = drive(fromLng, fromLat, toLng, toLat, departAt);
            return route.isComputed() ? route.durationMinutes() : UNREACHABLE_MINUTES;
        }
        final Integer cached = travelTimeCache.get(mode, fromLng, fromLat, toLng, toLat);
        if (cached != null) {
            return cached;
        }
        final TransitResult remembered = transitMemo.get().get(legKey(fromLng, fromLat, toLng, toLat));
        if (remembered != null) {
            return remembered.isComputed() ? remembered.totalMinutes() : UNREACHABLE_MINUTES;
        }
        final TransitResult transit = odsayTransitPort.findTransit(fromLng, fromLat, toLng, toLat);
        transitMemo.get().put(legKey(fromLng, fromLat, toLng, toLat), transit);
        final int minutes = transit.isComputed() ? transit.totalMinutes() : UNREACHABLE_MINUTES;
        if (minutes != UNREACHABLE_MINUTES) {
            travelTimeCache.put(mode, fromLng, fromLat, toLng, toLat, minutes);
        }
        return minutes;
    }

 /** 행렬에 필요한 대중교통 구간을 한꺼번에 받아 둔다. */
    private void prewarmTransit(List<Property> properties, BigDecimal startLat, BigDecimal startLng) {
        final List<double[]> points = new ArrayList<>();
        points.add(new double[]{startLng.doubleValue(), startLat.doubleValue()});
        properties.forEach(p -> points.add(new double[]{p.lng().doubleValue(), p.lat().doubleValue()}));

        final Map<String, double[]> pending = new LinkedHashMap<>();
        for (int i = 0; i < points.size(); i++) {
            for (int j = 1; j < points.size(); j++) {
                if (i == j) {
                    continue;
                }
                final double[] a = points.get(i);
                final double[] b = points.get(j);
                if (travelTimeCache.get(TravelMode.TRANSIT, a[0], a[1], b[0], b[1]) != null) {
                    continue;
                }
                pending.put(legKey(a[0], a[1], b[0], b[1]), new double[]{a[0], a[1], b[0], b[1]});
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        final Map<String, TransitResult> answered = odsayTransitPort.findTransitBatch(pending);
        answered.forEach((key, transit) -> {
            transitMemo.get().put(key, transit);
            if (transit.isComputed()) {
                final double[] c = pending.get(key);
                travelTimeCache.put(TravelMode.TRANSIT, c[0], c[1], c[2], c[3], transit.totalMinutes());
            }
        });
        pending.keySet().stream()
                .filter(key -> !answered.containsKey(key))
                .forEach(key -> transitMemo.get().put(key, TransitResult.missing()));
    }

 /** 미리 받아 둔 자동차 길을 먼저 본다. */
    private DriveRoute drive(double fromLng, double fromLat, double toLng, double toLat,
                             LocalDateTime departAt) {
        return driveMemo.get().computeIfAbsent(driveKey(fromLng, fromLat, toLng, toLat, departAt),
                key -> kakaoDirectionsPort.findRoute(fromLng, fromLat, toLng, toLat, departAt));
    }

 /** 출발 시각까지 열쇠에 넣는다. */
    private static String driveKey(double fromLng, double fromLat, double toLng, double toLat,
                                   LocalDateTime departAt) {
        return legKey(fromLng, fromLat, toLng, toLat) + "@" + departAt;
    }

 /** 좌표 넷을 하나의 열쇠로. 소수점 여섯 자리면 1m 안쪽이라 같은 지점으로 봐도 된다. */
    private static String legKey(double fromLng, double fromLat, double toLng, double toLat) {
        return String.format("%.6f,%.6f>%.6f,%.6f", fromLng, fromLat, toLng, toLat);
    }

    private TravelMode modeOf(TravelMode mode) {
        return mode == null ? TravelMode.DRIVING : mode;
    }

 /** 마지막 출발지를 돌려준다. 임장 플래너를 열 때 채워 넣는다. */
    public StartLocation lastStartLocation() {
        return startLocationCache.get(currentUserId()).orElse(null);
    }

 /** 출발지 입력이 끝난 시점에 캐시한다 (TTL 7일). */
    public StartLocation rememberStartLocation(StartLocation location) {
        startLocationCache.put(currentUserId(), location);
        return location;
    }

    private Long currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HalleyUserDetails principal) {
            return principal.getId();
        }
        return null;
    }
}
