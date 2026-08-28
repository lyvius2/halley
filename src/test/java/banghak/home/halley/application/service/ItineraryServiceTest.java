package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreatePlanRequest;
import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryRequest;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.VisitPlanResponse;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.external.KakaoDirectionsPort;
import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.domain.itinerary.DriveRoute;
import banghak.home.halley.domain.itinerary.TravelMode;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.scoring.TransitResult;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class ItineraryServiceTest {

    @TestConfiguration
    static class StubConfig {

        final java.util.concurrent.atomic.AtomicInteger transitCalls = new java.util.concurrent.atomic.AtomicInteger();

        @Bean
        @Primary
        KakaoDirectionsPort kakaoDirectionsPort() {
            return (fromLng, fromLat, toLng, toLat) -> new DriveRoute(10, 1000);
        }

        @Bean
        @Primary
        OdsayTransitPort odsayTransitPort() {
            return (sx, sy, ex, ey) -> {
                transitCalls.incrementAndGet();
                return new TransitResult(15, 1, 5);
            };
        }
    }

    @Autowired
    private StubConfig stubConfig;

    @Autowired
    private ItineraryService itineraryService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUpAuth() {
        stubConfig.transitCalls.set(0);
        if (userRepository.findByEmail("itinerary@example.com").isEmpty()) {
            userService.create(new CreateUserRequest(
                    "itinerary", "임장자", "itinerary@example.com", "pw12345!", UserRole.MEMBER, null, null, null, 0L, 60_000_000L, 0L));
        }
        final User user = userRepository.findByEmail("itinerary@example.com").orElseThrow();
        final HalleyUserDetails details = new HalleyUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("자가용으로 최적 순서와 총 소요시간을 계산한다")
    void optimizeDriving() {
        // given
        final List<Long> ids = List.of(
                propertyService.create(request("임장A")).id(),
                propertyService.create(request("임장B")).id(),
                propertyService.create(request("임장C")).id());

        // when
        final OptimizeItineraryResponse result = itineraryService.optimize(new OptimizeItineraryRequest(
                ids, TravelMode.DRIVING, new BigDecimal("37.5"), new BigDecimal("126.9")));

        // then — 3개 매물 + 출발지 → 이동 3회 × 10분
        assertThat(result.orderedPropertyIds()).hasSize(3);
        assertThat(result.orderedPropertyIds()).containsAll(ids);
        assertThat(result.totalMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("대중교통 모드로 최적 순서를 계산한다")
    void optimizeTransit() {
        // given
        final List<Long> ids = List.of(
                propertyService.create(request("버스A")).id(),
                propertyService.create(request("버스B")).id());

        // when
        final OptimizeItineraryResponse result = itineraryService.optimize(new OptimizeItineraryRequest(
                ids, TravelMode.TRANSIT, new BigDecimal("37.5"), new BigDecimal("126.9")));

        // then — 2개 매물 → 이동 2회 × 15분
        assertThat(result.orderedPropertyIds()).hasSize(2);
        assertThat(result.totalMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("계획을 저장·조회하고 방문완료를 토글·재계산한다")
    void planLifecycle() {
        // given
        final List<Long> ids = List.of(
                propertyService.create(request("플랜A")).id(),
                propertyService.create(request("플랜B")).id());

        // when — 저장
        final VisitPlanResponse plan = itineraryService.createPlan(new CreatePlanRequest(
                ids, TravelMode.DRIVING, new BigDecimal("37.5"), new BigDecimal("126.9"),
                "우리집", LocalDate.of(2026, 9, 1), LocalTime.of(9, 0), null, 25));

        // then
        assertThat(plan.stops()).hasSize(2);
        assertThat(plan.stops().getFirst().estimatedArrival()).isEqualTo(LocalTime.of(9, 10));
        assertThat(plan.stops().getFirst().travelMinutesFromPrev()).isEqualTo(10);

        // when — 방문완료 토글
        final Long stopId = plan.stops().getFirst().id();
        final VisitPlanResponse toggled = itineraryService.toggleStopVisited(plan.id(), stopId, true);

        // then
        assertThat(toggled.stops().getFirst().id()).isEqualTo(stopId);
        assertThat(itineraryService.getPlan(plan.id()).stops().getFirst().visited()).isTrue();
        assertThat(toggled.stops().getFirst().visited()).isTrue();

        // when — 재계산
        final VisitPlanResponse recomputed = itineraryService.recompute(plan.id());
        assertThat(recomputed.stops()).hasSize(2);
        assertThat(recomputed.stops().getFirst().visited()).isFalse();
    }

    @Test
    @DisplayName("대중교통 이동시간은 TTL 캐시로 재계산 시 조회하지 않는다")
    void transitTravelTimeCached() {
        // given — 좌표가 서로 다른 매물
        final List<Long> ids = List.of(
                propertyService.create(request("캐시A", "37.51", "126.91")).id(),
                propertyService.create(request("캐시B", "37.52", "126.92")).id());

        // when — 첫 계산은 캐시 미스 → ODsay 호출
        itineraryService.optimize(new OptimizeItineraryRequest(
                ids, TravelMode.TRANSIT, new BigDecimal("37.5"), new BigDecimal("126.9")));
        final int firstCalls = stubConfig.transitCalls.get();
        assertThat(firstCalls).isGreaterThan(0);

        // 같은 요청 재계산 → 캐시 히트로 추가 호출 없음
        itineraryService.optimize(new OptimizeItineraryRequest(
                ids, TravelMode.TRANSIT, new BigDecimal("37.5"), new BigDecimal("126.9")));

        // then
        assertThat(stubConfig.transitCalls.get()).isEqualTo(firstCalls);
    }

    private PropertyRequest request(String name) {
        return request(name, "37.5", "127.0");
    }

    private PropertyRequest request(String name, String lat, String lng) {
        return new PropertyRequest(
                name, null, DealType.SALE, 500_000_000L, null, null,
                "서울시", null, new BigDecimal(lat), new BigDecimal(lng),
                null, null, null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
