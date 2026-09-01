package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreatePlanRequest;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryRequest;
import banghak.home.halley.adapter.inbound.web.dto.ItineraryLegResponse;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryResponse;
import banghak.home.halley.adapter.inbound.web.dto.VisitPlanResponse;
import banghak.home.halley.adapter.inbound.web.dto.VisitPlanStopResponse;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyVisitPlanRepository;
import banghak.home.halley.adapter.outbound.persistence.VisitPlanStopRepository;
import banghak.home.halley.application.port.out.cache.TravelTimeCache;
import banghak.home.halley.application.port.out.external.KakaoDirectionsPort;
import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.application.port.out.cache.StartLocationCache;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.domain.itinerary.StartLocation;
import banghak.home.halley.config.exception.InvalidPlanRequestException;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.domain.itinerary.DriveRoute;
import banghak.home.halley.domain.itinerary.ItineraryOptimizer;
import banghak.home.halley.domain.itinerary.PlanStatus;
import banghak.home.halley.domain.itinerary.PropertyVisitPlan;
import banghak.home.halley.domain.itinerary.TravelCostMatrix;
import banghak.home.halley.domain.itinerary.TravelMode;
import banghak.home.halley.domain.itinerary.VisitPlanStop;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.TransitResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ItineraryService {

    private static final long DEPOT_ID = -1L;
    /**
     * 한 번의 계산 안에서만 쓰는 기억 (설계 I176).
     *
     * <p>행렬을 만들며 받은 `TransitResult` 를 구간 안내에서 다시 씁니다 —
     * 안 그러면 <b>같은 구간을 두 번 부릅니다.</b> 요청마다 비웁니다.
     */
    private final ThreadLocal<Map<String, TransitResult>> transitMemo =
            ThreadLocal.withInitial(HashMap::new);
    private static final int UNREACHABLE_MINUTES = 999;

    private final PropertyRepository propertyRepository;
    private final PropertyAccessGuard propertyAccessGuard;
    private final PropertyVisitPlanRepository propertyVisitPlanRepository;
    private final VisitPlanStopRepository visitPlanStopRepository;
    private final KakaoDirectionsPort kakaoDirectionsPort;
    private final OdsayTransitPort odsayTransitPort;
    private final TravelTimeCache travelTimeCache;
    private final ItineraryOptimizer optimizer;

    private final StartLocationCache startLocationCache;

    public ItineraryService(PropertyAccessGuard propertyAccessGuard,
                                  PropertyRepository propertyRepository,
                            PropertyVisitPlanRepository propertyVisitPlanRepository,
                            VisitPlanStopRepository visitPlanStopRepository,
                            KakaoDirectionsPort kakaoDirectionsPort,
                            OdsayTransitPort odsayTransitPort,
                            TravelTimeCache travelTimeCache,
                            ItineraryOptimizer optimizer,
                            StartLocationCache startLocationCache) {
        this.propertyAccessGuard = propertyAccessGuard;
        this.propertyRepository = propertyRepository;
        this.propertyVisitPlanRepository = propertyVisitPlanRepository;
        this.visitPlanStopRepository = visitPlanStopRepository;
        this.kakaoDirectionsPort = kakaoDirectionsPort;
        this.odsayTransitPort = odsayTransitPort;
        this.travelTimeCache = travelTimeCache;
        this.optimizer = optimizer;
        this.startLocationCache = startLocationCache;
    }

    public OptimizeItineraryResponse optimize(OptimizeItineraryRequest request) {
        transitMemo.get().clear();
        final TravelMode mode = modeOf(request.travelMode());
        final List<Property> properties = loadWithCoords(request.propertyIds());
        if (properties.isEmpty()) {
            return OptimizeItineraryResponse.empty();
        }
        final TravelCostMatrix matrix = buildMatrix(properties, request.startLat(), request.startLng(), mode);
        final List<Long> order = optimizer.optimize(DEPOT_ID, properties.stream().map(Property::id).toList(), matrix);
        return new OptimizeItineraryResponse(order, totalMinutes(order, matrix),
                legsOf(order, properties, request.startLat(), request.startLng(), mode));
    }

    /**
     * 정해진 순서를 따라가며 구간 안내와 경로선을 모은다 (설계 I176 · I177).
     *
     * <p><b>순서를 정한 뒤에 부릅니다.</b> 행렬을 만들 때 다 받아 두면 12개 매물에
     * 156번을 부르는데, 실제로 쓰는 것은 <b>11개 구간</b>뿐입니다.
     *
     * <p>한 구간이 실패해도 나머지는 채웁니다 — 경로선이 없으면 화면이 직선을 그립니다.
     */
    private List<ItineraryLegResponse> legsOf(List<Long> order, List<Property> properties,
                                              BigDecimal startLat, BigDecimal startLng, TravelMode mode) {
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
            legs.add(legOf(fromId, to, fromLng, fromLat, mode));
            fromId = toId;
            fromLat = to.lat().doubleValue();
            fromLng = to.lng().doubleValue();
        }
        return legs;
    }

    private ItineraryLegResponse legOf(Long fromId, Property to, double fromLng, double fromLat,
                                       TravelMode mode) {
        final double toLng = to.lng().doubleValue();
        final double toLat = to.lat().doubleValue();
        if (mode == TravelMode.DRIVING) {
            final DriveRoute route = kakaoDirectionsPort.findRoute(fromLng, fromLat, toLng, toLat);
            return ItineraryLegResponse.of(fromId, to.id(),
                    route.isComputed() ? route.durationMinutes() : UNREACHABLE_MINUTES,
                    List.of(), route.path());
        }
        final TransitResult remembered = transitMemo.get().get(legKey(fromLng, fromLat, toLng, toLat));
        final TransitResult transit = remembered != null
                ? remembered
                : odsayTransitPort.findTransit(fromLng, fromLat, toLng, toLat);
        return ItineraryLegResponse.of(fromId, to.id(),
                transit.isComputed() ? transit.totalMinutes() : UNREACHABLE_MINUTES,
                transit.legs(), odsayTransitPort.findLane(transit.mapObj()));
    }

    @Transactional
    public VisitPlanResponse createPlan(CreatePlanRequest request) {
        final TravelMode mode = modeOf(request.travelMode());
        final List<Property> properties = loadWithCoords(request.propertyIds());
        if (properties.isEmpty()) {
            throw new InvalidPlanRequestException("좌표가 있는 매물을 선택해 주세요");
        }
        final TravelCostMatrix matrix = buildMatrix(properties, request.startLat(), request.startLng(), mode);
        final List<Long> order = optimizer.optimize(DEPOT_ID, properties.stream().map(Property::id).toList(), matrix);

        final PropertyVisitPlan plan = propertyVisitPlanRepository.save(new PropertyVisitPlan(
                null,
                request.visitDate() == null ? LocalDate.now() : request.visitDate(),
                currentUserId(),
                request.startAddress(),
                request.startLat(),
                request.startLng(),
                mode,
                request.windowStart(),
                request.windowEnd(),
                request.stayMinutesDefault() == null ? 25 : request.stayMinutesDefault(),
                PlanStatus.DRAFT,
                Instant.now()));

        final List<VisitPlanStop> stops = buildStops(plan.id(), order, matrix,
                request.windowStart() == null ? LocalTime.of(9, 0) : request.windowStart(),
                request.stayMinutesDefault() == null ? 25 : request.stayMinutesDefault());
        final List<VisitPlanStop> savedStops = stops.stream()
                .map(visitPlanStopRepository::save)
                .toList();
        return toResponse(plan, savedStops);
    }

    public VisitPlanResponse getPlan(Long planId) {
        final PropertyVisitPlan plan = propertyVisitPlanRepository.findById(planId)
                .orElseThrow(NotFoundListingsException::new);
        return toResponse(plan, visitPlanStopRepository.findByPlanId(planId));
    }

    @Transactional
    public VisitPlanResponse toggleStopVisited(Long planId, Long stopId, boolean visited) {
        propertyVisitPlanRepository.findById(planId)
                .orElseThrow(NotFoundListingsException::new);
        visitPlanStopRepository.updateVisited(stopId, visited, visited ? Instant.now() : null);
        return getPlan(planId);
    }

    @Transactional
    public VisitPlanResponse recompute(Long planId) {
        final PropertyVisitPlan plan = propertyVisitPlanRepository.findById(planId)
                .orElseThrow(NotFoundListingsException::new);
        final List<Long> propertyIds = visitPlanStopRepository.findByPlanId(planId).stream()
                .map(VisitPlanStop::propertyId)
                .toList();
        final List<Property> properties = loadWithCoords(propertyIds);
        final TravelCostMatrix matrix = buildMatrix(properties, plan.startLat(), plan.startLng(), plan.travelMode());
        final List<Long> order = optimizer.optimize(DEPOT_ID, properties.stream().map(Property::id).toList(), matrix);

        visitPlanStopRepository.deleteByPlanId(planId);
        final List<VisitPlanStop> stops = buildStops(planId, order, matrix,
                plan.windowStart() == null ? LocalTime.of(9, 0) : plan.windowStart(),
                plan.stayMinutesDefault() == null ? 25 : plan.stayMinutesDefault());
        for (final VisitPlanStop stop : stops) {
            visitPlanStopRepository.save(stop);
        }
        return getPlan(planId);
    }

    private List<Property> loadWithCoords(List<Long> propertyIds) {
        return propertyIds.stream()
                .map(id -> propertyAccessGuard.require(id))
                .filter(p -> p.lat() != null && p.lng() != null)
                .toList();
    }

    private TravelCostMatrix buildMatrix(List<Property> properties, BigDecimal startLat, BigDecimal startLng,
                                         TravelMode mode) {
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
                        to.lng().doubleValue(), to.lat().doubleValue(), mode);
            }
            return travelTime(from.lng().doubleValue(), from.lat().doubleValue(),
                    to.lng().doubleValue(), to.lat().doubleValue(), mode);
        };
    }

    private List<VisitPlanStop> buildStops(Long planId, List<Long> order, TravelCostMatrix matrix,
                                           LocalTime startTime, int stayMinutes) {
        final List<VisitPlanStop> stops = new ArrayList<>();
        LocalTime time = startTime;
        long previous = DEPOT_ID;
        for (int i = 0; i < order.size(); i++) {
            final long propertyId = order.get(i);
            final int travel = matrix.minutes(previous, propertyId);
            final LocalTime arrival = time.plusMinutes(travel);
            final LocalTime departure = arrival.plusMinutes(stayMinutes);
            stops.add(new VisitPlanStop(
                    null, planId, propertyId, i, arrival, departure, travel,
                    null, false, null));
            time = departure;
            previous = propertyId;
        }
        return stops;
    }

    private int totalMinutes(List<Long> order, TravelCostMatrix matrix) {
        int total = 0;
        long previous = DEPOT_ID;
        for (final Long id : order) {
            total += matrix.minutes(previous, id);
            previous = id;
        }
        return total;
    }

    private int travelTime(double fromLng, double fromLat, double toLng, double toLat, TravelMode mode) {
        if (mode == TravelMode.DRIVING) {
            final DriveRoute route = kakaoDirectionsPort.findRoute(fromLng, fromLat, toLng, toLat);
            return route.isComputed() ? route.durationMinutes() : UNREACHABLE_MINUTES;
        }
        final Integer cached = travelTimeCache.get(mode, fromLng, fromLat, toLng, toLat);
        if (cached != null) {
            return cached;
        }
        final TransitResult transit = odsayTransitPort.findTransit(fromLng, fromLat, toLng, toLat);
        // 구간 안내를 만들 때 다시 부르지 않도록 기억해 둔다 (설계 I176).
        // 이 호출 한 번 안에서만 유효하다 — 행렬을 만들며 이미 받은 것을 그대로 쓴다
        transitMemo.get().put(legKey(fromLng, fromLat, toLng, toLat), transit);
        final int minutes = transit.isComputed() ? transit.totalMinutes() : UNREACHABLE_MINUTES;
        if (minutes != UNREACHABLE_MINUTES) {
            travelTimeCache.put(mode, fromLng, fromLat, toLng, toLat, minutes);
        }
        return minutes;
    }

    /** 좌표 넷을 하나의 열쇠로. 소수점 여섯 자리면 1m 안쪽이라 같은 지점으로 봐도 된다. */
    private static String legKey(double fromLng, double fromLat, double toLng, double toLat) {
        return String.format("%.6f,%.6f>%.6f,%.6f", fromLng, fromLat, toLng, toLat);
    }

    private VisitPlanResponse toResponse(PropertyVisitPlan plan, List<VisitPlanStop> stops) {
        final List<VisitPlanStopResponse> stopResponses = stops.stream()
                .sorted(java.util.Comparator.comparing(VisitPlanStop::stopOrder))
                .map(s -> new VisitPlanStopResponse(
                        s.id(), s.propertyId(), s.stopOrder(), s.estimatedArrival(),
                        s.estimatedDeparture(), s.travelMinutesFromPrev(), s.visited()))
                .toList();
        final int total = stopResponses.stream()
                .mapToInt(s -> s.travelMinutesFromPrev() == null ? 0 : s.travelMinutesFromPrev())
                .sum();
        return new VisitPlanResponse(
                plan.id(), plan.visitDate(), plan.travelMode(), plan.status(),
                plan.startAddress(), stopResponses, total);
    }

    private TravelMode modeOf(TravelMode mode) {
        return mode == null ? TravelMode.DRIVING : mode;
    }

    /** 마지막 출발지를 돌려준다 — 임장 플래너를 열 때 채워 넣는다 (설계 I52). */
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
