package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryRequest;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.domain.itinerary.TravelMode;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.scoring.TransitResult;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 한 번 실패한 것을 <b>구간마다 다시 묻지 않는다</b> (설계 I271).
 *
 * <p>ODsay 하루 한도가 끝난 날 이렇게 됐습니다.
 *
 * <pre>
 * Transit legs unresolved by ODsay - estimating. total=14, unresolved=9
 * LLM 60초 timeout ×2  →  answered 0 of 9
 * 그리고 구간마다 다시  →  answered 0 of 1  ×  여러 번  (몇 분)
 * </pre>
 *
 * <p>미리 받아 두기가 <b>못 받은 것을 기억하지 않아</b>, 구간 안내가 그 자리를
 * 하나씩 다시 물었습니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("대중교통 실패의 뒷일 (설계 I271)")
class TransitFallbackStormTest {

    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    /** 구간 하나씩 물어본 횟수. 여기가 0이 아니면 한 번의 실패가 구간 수만큼 늘어난 것이다. */
    static final AtomicInteger SINGLE_CALLS = new AtomicInteger();
    static final AtomicInteger BATCH_CALLS = new AtomicInteger();

    @TestConfiguration
    static class SpentQuota {

        @Bean
        @Primary
        OdsayTransitPort odsayTransitPort() {
            return new OdsayTransitPort() {

                @Override
                public TransitResult findTransit(double startX, double startY,
                                                 double endX, double endY) {
                    SINGLE_CALLS.incrementAndGet();
                    return TransitResult.missing();
                }

                @Override
                public Map<String, TransitResult> findTransitBatch(Map<String, double[]> legs) {
                    BATCH_CALLS.incrementAndGet();
                    // 한도가 끝났고 LLM 도 못 답했다 — <b>아무것도 못 돌려준다</b>
                    return Map.of();
                }
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
        SINGLE_CALLS.set(0);
        BATCH_CALLS.set(0);
        GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
        ids.clear();
        for (int i = 0; i < 4; i++) {
            ids.add(propertyService.create(request("동선매물" + i,
                    new BigDecimal("37.5" + i), new BigDecimal("127.0" + i))).id());
        }
    }

    @AfterEach
    void tearDown() {
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("한꺼번에 물어 실패했으면 구간마다 또 묻지 않는다")
    void doesNotReaskLegByLegAfterTheBatchFailed() {
        final OptimizeItineraryResponse response = itineraryService.optimize(new OptimizeItineraryRequest(
                ids, TravelMode.TRANSIT, new BigDecimal("37.55"), new BigDecimal("127.05"),
                LocalDate.now().plusDays(3), LocalTime.of(10, 0), 25));

        assertThat(BATCH_CALLS.get()).as("한꺼번에 묻기는 한 번 돌아야 한다").isEqualTo(1);
        assertThat(SINGLE_CALLS.get())
                .as("구간마다 %d번을 다시 물었다 — 한 번의 실패가 그만큼 늘어난다", SINGLE_CALLS.get())
                .isZero();
        // 그리고 화면은 모른다고 말해야 한다 (설계 I270)
        assertThat(response.legs()).isNotEmpty()
                .allSatisfy(leg -> assertThat(leg.minutes()).isNull());
        assertThat(response.unknownLegs()).isEqualTo(response.legs().size());
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
