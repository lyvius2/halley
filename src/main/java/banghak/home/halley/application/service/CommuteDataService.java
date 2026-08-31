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
        if (cached.isPresent() && cached.get().totalMinutes() != null) {
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
                // 경로가 없거나(도서·산간) ODsay가 거절한 경우. 저장하지 않으므로 다음에 다시 시도한다
                log.info("No commute for user - transit not computed. propertyId={}, userId={}",
                        property.id(), user.id());
                return null;
            }
            commuteResultRepository.upsert(new CommuteResult(
                    property.id(), user.id(), transit.totalMinutes(),
                    transit.transferCount(), transit.walkMinutes(), null, Instant.now()));
            return transit.totalMinutes();
        } catch (RuntimeException e) {
            log.warn("Commute lookup failed. propertyId={}, userId={}, cause={}", property.id(), user.id(), e.getMessage());
            return null;
        }
    }
}
