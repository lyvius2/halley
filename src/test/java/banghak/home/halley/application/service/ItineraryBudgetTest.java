package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryRequest;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryResponse;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.external.KakaoDirectionsPort;
import banghak.home.halley.domain.itinerary.DriveRoute;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.itinerary.TravelMode;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 임장 경로 계산이 <b>제한 시간 안에 끝나는가</b> (설계 I263).
 *
 * <p>운영에서 <b>504 Gateway Timeout</b> 이 났습니다. 매물 다섯이면 구간이
 * <b>25쌍</b>인데, 자동차 모드는 그것을 <b>한 줄로</b> 물었습니다 — 카카오 읽기
 * 제한이 6초이니 최악에 2분 반입니다. 프록시는 60초에 끊습니다.
 *
 * <p>게다가 자동차 길은 <b>아무 데도 담아 두지 않아</b> 행렬에서 한 번,
 * 구간 안내에서 <b>또 한 번</b> 같은 길을 물었습니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("임장 경로 계산의 시간 (설계 I263)")
class ItineraryBudgetTest {

    /** 보정이 끼면 배경 스레드가 길찾기를 또 불러 수가 흔들린다. */
    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    /** 한 번 부르는 데 걸리는 시간 — 느린 API를 흉내낸다. */
    private static final long CALL_MS = 300;

    static final List<String> CALLS = java.util.Collections.synchronizedList(new ArrayList<>());

    /** 길찾기를 아예 못 받는 상태를 만든다 (설계 I270). */
    static volatile boolean ROUTES_UNAVAILABLE = false;

    @TestConfiguration
    static class SlowDirections {

        @Bean
        @Primary
        KakaoDirectionsPort kakaoDirectionsPort() {
            return (fromLng, fromLat, toLng, toLat, departAt) -> {
                if (ROUTES_UNAVAILABLE) {
                    // 하루 한도가 끝났을 때 어댑터가 돌려주는 것
                    return DriveRoute.missing();
                }
                // 출발 시각까지 물음의 일부다 (설계 I196) — 같은 좌표라도 시각이 다르면 다른 질문이다
                CALLS.add(String.format("%.6f,%.6f>%.6f,%.6f@%s",
                        fromLng, fromLat, toLng, toLat, departAt));
                try {
                    Thread.sleep(CALL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new DriveRoute(10, 1_000);
            };
        }
    }

    @Autowired private ItineraryService itineraryService;
    @Autowired private PropertyService propertyService;
    @Autowired private UserGroupRepository userGroupRepository;
    @Autowired private UserRepository userRepository;

    private final List<Long> ids = new ArrayList<>();

    @BeforeEach
    void setUp() {
        CALLS.clear();
        ROUTES_UNAVAILABLE = false;
        GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
        ids.clear();
        for (int i = 0; i < 5; i++) {
            final PropertyResponse created = propertyService.create(
                    request("동선매물" + i, new BigDecimal("37.5" + i), new BigDecimal("127.0" + i)));
            ids.add(created.id());
        }
    }

    @AfterEach
    void tearDown() {
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("느린 길찾기라도 한 줄로 돌지 않는다")
    void doesNotCallDirectionsOneByOne() {
        final long startedAt = System.nanoTime();
        final OptimizeItineraryResponse response = itineraryService.optimize(optimizeRequest());
        final long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(response.orderedPropertyIds()).hasSize(5);
        // 25쌍 × 300ms = 7.5초. 동시에 돌면 그 몇 분의 일이다.
        // 여기가 5초를 넘으면 예전처럼 한 줄로 도는 것이다
        assertThat(elapsedMs)
                .as("구간 %d개를 받는 데 %dms 걸렸다", CALLS.size(), elapsedMs)
                .isLessThan(5_000);
    }

    @Test
    @DisplayName("똑같은 물음을 두 번 하지 않는다")
    void asksEachLegOnce() {
        itineraryService.optimize(optimizeRequest());

        final Set<String> distinct = new java.util.LinkedHashSet<>(CALLS);
        // 행렬에서 한 번, 구간 안내에서 또 한 번 물었다 — 담아 두지 않았기 때문이다.
        // 시각이 다른 물음은 다른 물음이다 (설계 I196) — 그건 줄이면 안 된다
        assertThat(CALLS)
                .as("길찾기 %d회 중 서로 다른 물음은 %d개뿐이다", CALLS.size(), distinct.size())
                .hasSameSizeAs(distinct);
    }

    @Test
    @DisplayName("이동시간을 못 받으면 999분이라 하지 않는다")
    void doesNotCallUnknownTravelTimeNineHundredNinetyNine() {
        // given — 카카오 하루 한도가 끝났다. 운영에서 실제로 이렇게 됐다
        ROUTES_UNAVAILABLE = true;

        // when
        final OptimizeItineraryResponse response = itineraryService.optimize(optimizeRequest());

        // then — 화면은 "999분"이라 말했고 합계는 3996분이라는 지어낸 수였다
        assertThat(response.legs())
                .as("모르는 것은 모른다고 해야 한다")
                .isNotEmpty()
                .allSatisfy(leg -> assertThat(leg.minutes()).isNull());
        assertThat(response.totalMinutes())
                .as("모르는 것을 더하면 합계가 거짓이 된다")
                .isZero();
        assertThat(response.unknownLegs())
                .as("몇 구간을 못 받았는지 화면이 말할 수 있어야 한다")
                .isEqualTo(response.legs().size());
        // 하나도 못 받았으면 <b>순서마저 뜻이 없다</b> — 늘어놓지 말고 말해야 한다 (설계 I274)
        assertThat(response.status())
                .as("결과가 아닌 것을 결과로 내놓으면 사람은 계산된 동선으로 읽는다")
                .isEqualTo(OptimizeItineraryResponse.Status.UNAVAILABLE);
        assertThat(response.message()).contains("내일 다시 시도");
    }

    @Test
    @DisplayName("받은 구간만 합계에 넣는다")
    void sumsOnlyWhatItKnows() {
        final OptimizeItineraryResponse response = itineraryService.optimize(optimizeRequest());

        assertThat(response.unknownLegs()).isZero();
        assertThat(response.status()).isEqualTo(OptimizeItineraryResponse.Status.OK);
        assertThat(response.message()).as("멀쩡할 때 경고 문구가 실리면 안 된다").isNull();
        assertThat(response.totalMinutes())
                .as("구간 %d개 × 10분", response.legs().size())
                .isEqualTo(response.legs().size() * 10);
    }

    private OptimizeItineraryRequest optimizeRequest() {
        return new OptimizeItineraryRequest(ids, TravelMode.DRIVING,
                new BigDecimal("37.55"), new BigDecimal("127.05"),
                LocalDate.now().plusDays(3), LocalTime.of(10, 0), 25);
    }

    private PropertyRequest request(String name, BigDecimal lat, BigDecimal lng) {
        return new PropertyRequest(
                name, null, DealType.SALE, 800_000_000L, null,
                null, "서울시 성북구 정릉동 1037", lat, lng,
                null, new BigDecimal("84.9"), null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
