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
import banghak.home.halley.domain.itinerary.StartLocation;
import banghak.home.halley.domain.itinerary.DriveRoute;
import banghak.home.halley.domain.itinerary.ItineraryOptimizer;
import banghak.home.halley.domain.itinerary.PropertyVisit;
import banghak.home.halley.domain.itinerary.TravelCostMatrix;
import banghak.home.halley.domain.itinerary.TravelMode;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.TransitResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@lombok.extern.slf4j.Slf4j
@Service
public class ItineraryService {

    private static final long DEPOT_ID = -1L;
    /** 출발지 캐시(I52)와 같은 수명 — 임장 준비는 며칠에 걸친다. */
    private static final java.time.Duration DRAFT_TTL = java.time.Duration.ofDays(7);
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
    private final PropertyVisitRepository propertyVisitRepository;
    private final KakaoDirectionsPort kakaoDirectionsPort;
    private final OdsayTransitPort odsayTransitPort;
    private final TravelTimeCache travelTimeCache;
    private final ItineraryOptimizer optimizer;

    private final StartLocationCache startLocationCache;
    private final CachePort cache;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public ItineraryService(PropertyAccessGuard propertyAccessGuard,
                                  PropertyRepository propertyRepository,
                            PropertyVisitRepository propertyVisitRepository,
                            KakaoDirectionsPort kakaoDirectionsPort,
                            OdsayTransitPort odsayTransitPort,
                            TravelTimeCache travelTimeCache,
                            ItineraryOptimizer optimizer,
                            StartLocationCache startLocationCache,
                            CachePort cache,
                            tools.jackson.databind.ObjectMapper objectMapper) {
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
    }

    /**
     * 작업 중인 것을 사용자별로 담아 둔다 (설계 I179).
     *
     * <p><b>계정마다 다릅니다.</b> 예전에는 화면 상태로만 있어, 로그아웃하지 않고
     * 다른 계정으로 들어오면 <b>앞 사람이 짜던 동선이 그대로 보였습니다.</b>
     *
     * <p>출발지(I52)와 같은 수명(7일)을 줍니다 — 임장 준비는 며칠에 걸칩니다.
     */
    public ItineraryDraft loadDraft() {
        return cache.get(CachePort.ITINERARY, String.valueOf(currentUserId()))
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, ItineraryDraft.class);
                    } catch (RuntimeException e) {
                        // 담아 둔 모양이 바뀌었을 수 있다. 버리고 빈 것으로 시작한다
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
        final TravelMode mode = modeOf(request.travelMode());
        final List<Property> properties = loadWithCoords(request.propertyIds());
        if (properties.isEmpty()) {
            return OptimizeItineraryResponse.empty();
        }
        final LocalDateTime departAt = departAt(request.visitDate(), request.windowStart());
        if (mode == TravelMode.TRANSIT) {
            prewarmTransit(properties, request.startLat(), request.startLng());
        }
        final TravelCostMatrix matrix =
                buildMatrix(properties, request.startLat(), request.startLng(), mode, departAt);
        final List<Long> order = optimizer.optimize(DEPOT_ID, properties.stream().map(Property::id).toList(), matrix);
        return new OptimizeItineraryResponse(order, totalMinutes(order, matrix),
                legsOf(order, properties, request.startLat(), request.startLng(), mode,
                        departAt, request.stayMinutes() == null ? 25 : request.stayMinutes()));
    }

    /**
     * 언제 출발하는가 (설계 I196).
     *
     * <p><b>날짜가 없으면 null 을 돌려줍니다.</b> 오늘로 채워 넣으면 다음 주말 계획에
     * 오늘의 길이 섞이는데, 그 어긋남은 화면에 드러나지 않습니다. 모르면 모르는 채로
     * 두어 "지금 기준"임이 분명하게 합니다.
     */
    private static LocalDateTime departAt(LocalDate visitDate, LocalTime windowStart) {
        return visitDate == null
                ? null
                : visitDate.atTime(windowStart == null ? LocalTime.of(9, 0) : windowStart);
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
            // 세 번째 매물의 길은 09시가 아니라 13시의 길이다 — 이동한 만큼, 머문 만큼 미룬다
            if (departAt != null) {
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
            final DriveRoute route = kakaoDirectionsPort.findRoute(fromLng, fromLat, toLng, toLat, departAt);
            return ItineraryLegResponse.of(fromId, to.id(),
                    route.isComputed() ? route.durationMinutes() : UNREACHABLE_MINUTES,
                    route.roads(), route.path());
        }
        final TransitResult remembered = transitMemo.get().get(legKey(fromLng, fromLat, toLng, toLat));
        final TransitResult transit = remembered != null
                ? remembered
                : odsayTransitPort.findTransit(fromLng, fromLat, toLng, toLat);
        return ItineraryLegResponse.of(fromId, to.id(),
                transit.isComputed() ? transit.totalMinutes() : UNREACHABLE_MINUTES,
                transit.legs(), odsayTransitPort.findLane(transit.mapObj()));
    }

    /**
     * 가 본 곳 (설계 I197).
     *
     * <p>계획을 저장하지 않으므로 <b>여기가 유일하게 DB에 남는 것</b>입니다.
     * 계산 결과는 캐시(I179)에 7일이면 충분하지만, 어디를 가 봤는지는 그렇지 않습니다.
     */
    public List<Long> visitedPropertyIds() {
        return propertyVisitRepository.findByUser(currentUserId()).stream()
                .map(PropertyVisit::propertyId)
                .toList();
    }

    /**
     * 방문완료를 켜고 끈다 (설계 I197).
     *
     * <p><b>내 그룹의 매물인지 먼저 봅니다.</b> 남의 매물 번호를 넣어 방문 기록을
     * 심을 수 있으면 안 됩니다.
     */
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

    private int totalMinutes(List<Long> order, TravelCostMatrix matrix) {
        int total = 0;
        long previous = DEPOT_ID;
        for (final Long id : order) {
            total += matrix.minutes(previous, id);
            previous = id;
        }
        return total;
    }

    private int travelTime(double fromLng, double fromLat, double toLng, double toLat, TravelMode mode,
                           LocalDateTime departAt) {
        if (mode == TravelMode.DRIVING) {
            final DriveRoute route = kakaoDirectionsPort.findRoute(fromLng, fromLat, toLng, toLat, departAt);
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

    /**
     * 행렬에 필요한 대중교통 구간을 <b>한꺼번에</b> 받아 둔다 (설계 I210).
     *
     * <p>ODsay 라면 쌍마다 불러도 괜찮지만(50ms), 하루치를 다 써 LLM 으로 넘어가면
     * 쌍마다 부르는 것은 <b>못 씁니다</b> — 매물 8개면 64쌍이라 한 번 계산에 수십 분입니다.
     *
     * <p><b>Held-Karp 가 실제로 보는 쌍만</b> 담습니다. 출발지로 <b>돌아오는</b> 구간은
     * 쓰지 않습니다 — 임장은 편도라 돌아오는 시간을 세지 않습니다.
     *
     * <p>이미 캐시에 있는 쌍은 뺍니다. 못 받은 쌍은 그냥 둡니다 — `travelTime` 이
     * 평소대로 하나씩 물어보고, 그래도 없으면 999분입니다.
     */
    private void prewarmTransit(List<Property> properties, BigDecimal startLat, BigDecimal startLng) {
        final List<double[]> points = new ArrayList<>();
        points.add(new double[]{startLng.doubleValue(), startLat.doubleValue()});
        properties.forEach(p -> points.add(new double[]{p.lng().doubleValue(), p.lat().doubleValue()}));

        final Map<String, double[]> pending = new LinkedHashMap<>();
        for (int i = 0; i < points.size(); i++) {
            // 도착이 출발지(0)인 구간은 안 쓴다 — 편도다
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
        odsayTransitPort.findTransitBatch(pending).forEach((key, transit) -> {
            // 구간 안내에서 다시 부르지 않도록 기억해 둔다 (설계 I176)
            transitMemo.get().put(key, transit);
            if (transit.isComputed()) {
                final double[] c = pending.get(key);
                travelTimeCache.put(TravelMode.TRANSIT, c[0], c[1], c[2], c[3], transit.totalMinutes());
            }
        });
    }

    /** 좌표 넷을 하나의 열쇠로. 소수점 여섯 자리면 1m 안쪽이라 같은 지점으로 봐도 된다. */
    private static String legKey(double fromLng, double fromLat, double toLng, double toLat) {
        return String.format("%.6f,%.6f>%.6f,%.6f", fromLng, fromLat, toLng, toLat);
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
