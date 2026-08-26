package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.adapter.outbound.persistence.CommuteResultRepository;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.CommuteResult;
import banghak.home.halley.domain.scoring.TransitResult;
import banghak.home.halley.domain.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class CommuteDataService {

    private final OdsayTransitPort odsayTransitPort;
    private final CommuteResultRepository commuteResultRepository;

    public CommuteDataService(OdsayTransitPort odsayTransitPort,
                              CommuteResultRepository commuteResultRepository) {
        this.odsayTransitPort = odsayTransitPort;
        this.commuteResultRepository = commuteResultRepository;
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

    private Integer ensureForUser(Property property, User user) {
        if (user.workplaceLat() == null || user.workplaceLng() == null) {
            return null;
        }
        final Optional<CommuteResult> cached = commuteResultRepository.findById(property.id(), user.id());
        if (cached.isPresent() && cached.get().totalMinutes() != null) {
            return cached.get().totalMinutes();
        }
        try {
            final TransitResult transit = odsayTransitPort.findTransit(
                    user.workplaceLng().doubleValue(), user.workplaceLat().doubleValue(),
                    property.lng().doubleValue(), property.lat().doubleValue());
            if (!transit.isComputed()) {
                return null;
            }
            commuteResultRepository.upsert(new CommuteResult(
                    property.id(), user.id(), transit.totalMinutes(),
                    transit.transferCount(), transit.walkMinutes(), null, Instant.now()));
            return transit.totalMinutes();
        } catch (RuntimeException e) {
            log.warn("통근 조회 실패 propertyId={} userId={}: {}", property.id(), user.id(), e.getMessage());
            return null;
        }
    }
}
