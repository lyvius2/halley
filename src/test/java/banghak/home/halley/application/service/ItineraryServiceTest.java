package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryRequest;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.external.KakaoDirectionsPort;
import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.domain.itinerary.DriveRoute;
import banghak.home.halley.domain.itinerary.TravelMode;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.scoring.TransitResult;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
class ItineraryServiceTest {

    @TestConfiguration
    static class StubConfig {

        final java.util.concurrent.atomic.AtomicInteger transitCalls = new java.util.concurrent.atomic.AtomicInteger();

        /** 자가용 길찾기에 실린 출발 시각. 순서대로 쌓인다 (설계 I196). */
        final java.util.List<java.time.LocalDateTime> departures =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        @Bean
        @Primary
        KakaoDirectionsPort kakaoDirectionsPort() {
            return (fromLng, fromLat, toLng, toLat, departAt) -> {
                departures.add(departAt);
                return new DriveRoute(10, 1000);
            };
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
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository groupTestUserRepository;

    /** 매물은 그룹에 딸리므로 그룹에 속한 회원으로 로그인해 둔다 (설계 I87). */
    @BeforeEach
    void loginAsGroupMember() {
        GroupTestSupport.loginAsGroupMember(userGroupRepository, groupTestUserRepository);
    }

    @AfterEach
    void clearLogin() {
        GroupTestSupport.logout();
    }

    @Autowired
    private StubConfig stubConfig;

    /**
     * 이 테스트는 이동시간 캐시만 본다. 비동기 보정이 끝나며 다시 채점하면
     * 통근 조회가 딸려 가 호출 횟수가 어긋난다 — 보정 자체는 다른 테스트가 본다.
     */
    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

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
        if (userRepository.findByLoginId("itinerary").isEmpty()) {
            userService.create(new CreateUserRequest(
                    "itinerary", "임장자", null, "pw12345!", UserRole.MEMBER, null, null, null, 0L, 60_000_000L, 0L));
        }
        final User user = userRepository.findByLoginId("itinerary").orElseThrow();
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
                ids, TravelMode.DRIVING, new BigDecimal("37.5"), new BigDecimal("126.9"), null, null, null));

        // then — 3개 매물 + 출발지 → 이동 3회 × 10분
        assertThat(result.orderedPropertyIds()).hasSize(3);
        assertThat(result.orderedPropertyIds()).containsAll(ids);
        assertThat(result.totalMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("임장 날짜·시각을 카카오에 실어 보낸다 — 시각만으로는 요일을 모른다 (설계 I196)")
    void sendsDepartureDateTime() {
        // given
        final List<Long> ids = List.of(propertyService.create(request("날짜A")).id());
        stubConfig.departures.clear();

        // when — 일요일 14시
        itineraryService.optimize(new OptimizeItineraryRequest(
                ids, TravelMode.DRIVING, new BigDecimal("37.5"), new BigDecimal("126.9"),
                LocalDate.of(2026, 9, 6), LocalTime.of(14, 0), 25));

        // then
        assertThat(stubConfig.departures).isNotEmpty();
        assertThat(stubConfig.departures).allMatch(d -> d != null
                && d.toLocalDate().equals(LocalDate.of(2026, 9, 6)));
    }

    @Test
    @DisplayName("날짜가 없으면 null을 넘긴다 — 오늘로 채우면 다음 주말 계획에 오늘 길이 섞인다")
    void noDateMeansNow() {
        // given
        final List<Long> ids = List.of(propertyService.create(request("날짜B")).id());
        stubConfig.departures.clear();

        // when
        itineraryService.optimize(new OptimizeItineraryRequest(
                ids, TravelMode.DRIVING, new BigDecimal("37.5"), new BigDecimal("126.9"),
                null, LocalTime.of(14, 0), 25));

        // then
        assertThat(stubConfig.departures).isNotEmpty();
        assertThat(stubConfig.departures).containsOnlyNulls();
    }

    @Test
    @DisplayName("뒤 구간은 그만큼 늦게 출발한다 — 세 번째 매물의 길은 09시가 아니다 (설계 I196)")
    void laterLegsDepartLater() {
        // given — 매물 셋, 이동 10분 + 체류 30분
        final List<Long> ids = List.of(
                propertyService.create(request("누적A")).id(),
                propertyService.create(request("누적B")).id(),
                propertyService.create(request("누적C")).id());

        // when
        itineraryService.optimize(new OptimizeItineraryRequest(
                ids, TravelMode.DRIVING, new BigDecimal("37.5"), new BigDecimal("126.9"),
                LocalDate.of(2026, 9, 6), LocalTime.of(9, 0), 30));

        // then — 구간 안내는 행렬 계산 뒤에 온다. 마지막 셋이 구간 셋이다
        final List<LocalDateTime> legDepartures =
                stubConfig.departures.subList(stubConfig.departures.size() - 3, stubConfig.departures.size());
        assertThat(legDepartures).containsExactly(
                LocalDateTime.of(2026, 9, 6, 9, 0),
                LocalDateTime.of(2026, 9, 6, 9, 40),
                LocalDateTime.of(2026, 9, 6, 10, 20));
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
                ids, TravelMode.TRANSIT, new BigDecimal("37.5"), new BigDecimal("126.9"), null, null, null));

        // then — 2개 매물 → 이동 2회 × 15분
        assertThat(result.orderedPropertyIds()).hasSize(2);
        assertThat(result.totalMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("방문완료는 계산 결과와 따로 남는다 — 계획을 저장하지 않는다 (설계 I197)")
    void visitedSurvivesWithoutAPlan() {
        // given
        final Long id = propertyService.create(request("방문A")).id();

        // when
        itineraryService.markVisited(id, true);

        // then — 계산을 다시 하든 말든, 저장 버튼을 누르지 않아도 남는다
        assertThat(itineraryService.visitedPropertyIds()).contains(id);
    }

    @Test
    @DisplayName("체크를 풀면 지워진다")
    void unmarkRemovesIt() {
        // given
        final Long id = propertyService.create(request("방문B")).id();
        itineraryService.markVisited(id, true);

        // when
        itineraryService.markVisited(id, false);

        // then
        assertThat(itineraryService.visitedPropertyIds()).doesNotContain(id);
    }

    @Test
    @DisplayName("같은 그룹이라도 남이 간 곳이 내 것으로 보이지 않는다 (설계 I197)")
    void visitsAreNotSharedWithinTheGroup() {
        // given — 같은 그룹의 매물을 A가 방문 체크
        final Long groupId = GroupTestSupport.loginAsGroupMember(userGroupRepository, groupTestUserRepository);
        final Long id = propertyService.create(request("공유매물")).id();
        itineraryService.markVisited(id, true);

        // when — 같은 그룹의 다른 사람으로 갈아탄다
        GroupTestSupport.login(groupTestUserRepository.save(new User(
                null, "동료" + id, "동료닉" + id, groupId, "hash", UserRole.MEMBER,
                null, null, null, false, false, 0L, 0L, 0L, true, null, null, Instant.now())));

        // then — 매물은 보이지만 A의 방문 기록은 아니다. 임장은 각자 간다
        assertThat(propertyService.get(id)).isNotNull();
        assertThat(itineraryService.visitedPropertyIds()).doesNotContain(id);
    }

    @Test
    @DisplayName("남의 매물에는 방문 기록을 심을 수 없다")
    void cannotMarkAnotherGroupsProperty() {
        // given — 내 그룹의 매물
        final Long id = propertyService.create(request("남의매물")).id();

        // when — 다른 그룹 사람으로 갈아탄다
        GroupTestSupport.loginAsGroupMember(userGroupRepository, groupTestUserRepository);

        // then — 매물 번호를 알아도 방문 기록을 심을 수 없다
        assertThatThrownBy(() -> itineraryService.markVisited(id, true))
                .isInstanceOf(NotFoundListingsException.class);
        assertThat(itineraryService.visitedPropertyIds()).isEmpty();
    }

    /**
     * 재계산이 <b>훨씬</b> 싸야 한다 (설계 I52 · I176).
     *
     * <p>이동시간 캐시(TTL 7일)가 <b>행렬</b>을 받습니다 — n개 매물이면 n×(n+1) 칸입니다.
     * 다만 구간 안내(설계 I176)는 <b>분 단위 캐시에 담기지 않아</b> 매번 다시 받습니다.
     * 정해진 순서의 <b>구간 수만큼</b>이라 행렬보다 훨씬 적습니다.
     *
     * <p>그래서 "추가 호출 0" 이 아니라 <b>"행렬만큼은 안 부른다"</b>를 봅니다.
     */
    @Test
    @DisplayName("재계산은 행렬을 다시 받지 않는다 — 구간 안내 몫만 부른다")
    void transitTravelTimeCached() {
        // given — 좌표가 서로 다른 매물
        final List<Long> ids = List.of(
                propertyService.create(request("캐시A", "37.51", "126.91")).id(),
                propertyService.create(request("캐시B", "37.52", "126.92")).id());

        // when — 첫 계산은 캐시 미스 → 행렬 + 구간 안내
        itineraryService.optimize(new OptimizeItineraryRequest(
                ids, TravelMode.TRANSIT, new BigDecimal("37.5"), new BigDecimal("126.9"), null, null, null));
        final int firstCalls = stubConfig.transitCalls.get();
        assertThat(firstCalls).isGreaterThan(0);

        // 같은 요청 재계산
        stubConfig.transitCalls.set(0);
        itineraryService.optimize(new OptimizeItineraryRequest(
                ids, TravelMode.TRANSIT, new BigDecimal("37.5"), new BigDecimal("126.9"), null, null, null));

        // then — 매물 2개면 구간은 2개다. 행렬(6칸)을 다시 받지 않는다
        assertThat(stubConfig.transitCalls.get()).isEqualTo(ids.size());
        assertThat(stubConfig.transitCalls.get()).isLessThan(firstCalls);
    }

    private PropertyRequest request(String name) {
        return request(name, "37.5", "127.0");
    }

    private PropertyRequest request(String name, String lat, String lng) {
        return new PropertyRequest(
                name, null, DealType.SALE, 500_000_000L, null,
                "서울시", null, new BigDecimal(lat), new BigDecimal(lng),
                null, null, null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
