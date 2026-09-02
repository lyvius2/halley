package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryRequest;
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

    @TestConfiguration
    static class SlowDirections {

        @Bean
        @Primary
        KakaoDirectionsPort kakaoDirectionsPort() {
            return (fromLng, fromLat, toLng, toLat, departAt) -> {
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
