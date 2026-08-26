package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreatePlanRequest;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryRequest;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryResponse;
import banghak.home.halley.adapter.inbound.web.dto.VisitPlanResponse;
import banghak.home.halley.adapter.inbound.web.dto.VisitPlanStopResponse;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyVisitPlanRepository;
import banghak.home.halley.adapter.outbound.persistence.VisitPlanStopRepository;
import banghak.home.halley.application.port.out.external.KakaoDirectionsPort;
import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.config.HalleyUserDetails;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ItineraryService {

    private static final long DEPOT_ID = -1L;
    private static final int UNREACHABLE_MINUTES = 999;

    private final PropertyRepository propertyRepository;
    private final PropertyVisitPlanRepository propertyVisitPlanRepository;
    private final VisitPlanStopRepository visitPlanStopRepository;
    private final KakaoDirectionsPort kakaoDirectionsPort;
    private final OdsayTransitPort odsayTransitPort;
    private final ItineraryOptimizer optimizer;

    public ItineraryService(PropertyRepository propertyRepository,
                            PropertyVisitPlanRepository propertyVisitPlanRepository,
                            VisitPlanStopRepository visitPlanStopRepository,
                            KakaoDirectionsPort kakaoDirectionsPort,
                            OdsayTransitPort odsayTransitPort,
                            ItineraryOptimizer optimizer) {
        this.propertyRepository = propertyRepository;
        this.propertyVisitPlanRepository = propertyVisitPlanRepository;
        this.visitPlanStopRepository = visitPlanStopRepository;
        this.kakaoDirectionsPort = kakaoDirectionsPort;
        this.odsayTransitPort = odsayTransitPort;
        this.optimizer = optimizer;
    }

    public OptimizeItineraryResponse optimize(OptimizeItineraryRequest request) {
        final TravelMode mode = modeOf(request.travelMode());
        final List<Property> properties = loadWithCoords(request.propertyIds());
        if (properties.isEmpty()) {
            return new OptimizeItineraryResponse(List.of(), 0);
        }
        final TravelCostMatrix matrix = buildMatrix(properties, request.startLat(), request.startLng(), mode);
        final List<Long> order = optimizer.optimize(DEPOT_ID, properties.stream().map(Property::id).toList(), matrix);
        return new OptimizeItineraryResponse(order, totalMinutes(order, matrix));
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
                .map(id -> propertyRepository.findById(id).orElseThrow(NotFoundListingsException::new))
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
        final TransitResult transit = odsayTransitPort.findTransit(fromLng, fromLat, toLng, toLat);
        return transit.isComputed() ? transit.totalMinutes() : UNREACHABLE_MINUTES;
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

    private Long currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HalleyUserDetails principal) {
            return principal.getId();
        }
        return null;
    }
}
