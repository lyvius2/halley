package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryRequest;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryResponse;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.application.port.out.external.KakaoDirectionsPort;
import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.domain.itinerary.DriveRoute;
import banghak.home.halley.domain.itinerary.ItineraryOptimizer;
import banghak.home.halley.domain.itinerary.TravelCostMatrix;
import banghak.home.halley.domain.itinerary.TravelMode;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.TransitResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ItineraryService {

    private static final long DEPOT_ID = -1L;
    private static final int UNREACHABLE_MINUTES = 999;

    private final PropertyRepository propertyRepository;
    private final KakaoDirectionsPort kakaoDirectionsPort;
    private final OdsayTransitPort odsayTransitPort;
    private final ItineraryOptimizer optimizer;

    public ItineraryService(PropertyRepository propertyRepository,
                            KakaoDirectionsPort kakaoDirectionsPort,
                            OdsayTransitPort odsayTransitPort,
                            ItineraryOptimizer optimizer) {
        this.propertyRepository = propertyRepository;
        this.kakaoDirectionsPort = kakaoDirectionsPort;
        this.odsayTransitPort = odsayTransitPort;
        this.optimizer = optimizer;
    }

    public OptimizeItineraryResponse optimize(OptimizeItineraryRequest request) {
        final TravelMode mode = request.travelMode() == null ? TravelMode.DRIVING : request.travelMode();
        final List<Property> properties = request.propertyIds().stream()
                .map(id -> propertyRepository.findById(id).orElseThrow(NotFoundListingsException::new))
                .filter(p -> p.lat() != null && p.lng() != null)
                .toList();
        if (properties.isEmpty()) {
            return new OptimizeItineraryResponse(List.of(), 0);
        }
        final List<Long> nodeIds = properties.stream().map(Property::id).toList();
        final Map<Long, Property> byId = properties.stream()
                .collect(Collectors.toMap(Property::id, Function.identity()));

        final TravelCostMatrix matrix = new TravelCostMatrix() {
            @Override
            public int minutes(long fromId, long toId) {
                final Property from = fromId == DEPOT_ID ? null : byId.get(fromId);
                final Property to = byId.get(toId);
                if (to == null) {
                    return UNREACHABLE_MINUTES;
                }
                if (from == null) {
                    return travelTime(request.startLat(), request.startLng(), to, mode);
                }
                return travelTime(from, to, mode);
            }
        };

        final List<Long> order = optimizer.optimize(DEPOT_ID, nodeIds, matrix);
        return new OptimizeItineraryResponse(order, totalMinutes(order, matrix));
    }

    private int travelTime(Property from, Property to, TravelMode mode) {
        return travelTime(from.lng().doubleValue(), from.lat().doubleValue(),
                to.lng().doubleValue(), to.lat().doubleValue(), mode);
    }

    private int travelTime(java.math.BigDecimal fromLng, java.math.BigDecimal fromLat,
                           Property to, TravelMode mode) {
        return travelTime(fromLng.doubleValue(), fromLat.doubleValue(),
                to.lng().doubleValue(), to.lat().doubleValue(), mode);
    }

    private int travelTime(double fromLng, double fromLat, double toLng, double toLat, TravelMode mode) {
        if (mode == TravelMode.DRIVING) {
            final DriveRoute route = kakaoDirectionsPort.findRoute(fromLng, fromLat, toLng, toLat);
            return route.isComputed() ? route.durationMinutes() : UNREACHABLE_MINUTES;
        }
        final TransitResult transit = odsayTransitPort.findTransit(fromLng, fromLat, toLng, toLat);
        return transit.isComputed() ? transit.totalMinutes() : UNREACHABLE_MINUTES;
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
}
