package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.forecast.FactorWeight;
import banghak.home.halley.domain.forecast.ForecastConfidence;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.forecast.PriceForecast;
import banghak.home.halley.domain.forecast.PriceOutlook;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.application.service.PropertyService;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("가격 전망 저장 (설계 I135·I138)")
class PriceForecastRepositoryTest {

    @Autowired
    private PriceForecastRepository repository;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository userRepository;

    private Long propertyId;

    @BeforeEach
    void setUp() {
        GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
        propertyId = propertyService.create(new PropertyRequest(
                "전망단지", null, DealType.SALE, 1_140_000_000L, null,
                "서울시 도로명주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null)).id();
    }

    @AfterEach
    void tearDown() {
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("요인과 유의사항이 JSON으로 왕복한다")
    void roundTripsFactorsAndCaveats() {
        // given
        final var outlook = new PriceOutlook(
                ForecastDirection.DOWN, ForecastConfidence.MEDIUM, 12,
                List.of(new PriceFactor("실거래 추세", ForecastDirection.DOWN, FactorWeight.HIGH,
                        "직전 3개월 중앙값 12억 1,000만원 → 최근 11억 4,000만원 (-5.8%)")),
                List.of("정책 변화는 반영하지 못했습니다", "표본이 8건으로 적습니다"));

        // when
        repository.upsert(new PriceForecast(null, propertyId, outlook,
                ForecastDirection.FLAT, "hash1", "claude", Instant.now()));

        // then
        final var found = repository.findByPropertyId(propertyId).orElseThrow();
        assertThat(found.outlook().direction()).isEqualTo(ForecastDirection.DOWN);
        assertThat(found.outlook().confidence()).isEqualTo(ForecastConfidence.MEDIUM);
        assertThat(found.outlook().factors()).hasSize(1);
        assertThat(found.outlook().factors().getFirst().evidence()).contains("11억 4,000만원");
        assertThat(found.outlook().factors().getFirst().weight()).isEqualTo(FactorWeight.HIGH);
        assertThat(found.outlook().caveats()).hasSize(2);
    }

    @Test
    @DisplayName("코드 예측을 함께 저장한다 — 없으면 사후 검증이 불가능하다")
    void storesBothPredictions() {
        repository.upsert(new PriceForecast(null, propertyId,
                new PriceOutlook(ForecastDirection.UP, ForecastConfidence.LOW, 12, List.of(), List.of()),
                ForecastDirection.FLAT, "hash1", "claude", Instant.now()));

        final var found = repository.findByPropertyId(propertyId).orElseThrow();
        assertThat(found.outlook().direction()).isEqualTo(ForecastDirection.UP);
        assertThat(found.codeDirection()).isEqualTo(ForecastDirection.FLAT);
        assertThat(found.agreed()).isFalse();
    }

    @Test
    @DisplayName("매물당 하나만 남는다 — 다시 내면 갈아 끼운다")
    void keepsOnlyOnePerProperty() {
        repository.upsert(new PriceForecast(null, propertyId,
                new PriceOutlook(ForecastDirection.UP, ForecastConfidence.HIGH, 12, List.of(), List.of()),
                ForecastDirection.UP, "hash1", "claude", Instant.now()));
        repository.upsert(new PriceForecast(null, propertyId,
                new PriceOutlook(ForecastDirection.DOWN, ForecastConfidence.LOW, 12, List.of(), List.of()),
                ForecastDirection.DOWN, "hash2", "claude", Instant.now()));

        final var found = repository.findByPropertyId(propertyId).orElseThrow();
        assertThat(found.outlook().direction()).isEqualTo(ForecastDirection.DOWN);
        assertThat(found.promptHash()).isEqualTo("hash2");
    }

    @Test
    @DisplayName("LLM 없이 낸 전망은 model이 비어 있다")
    void allowsNullModel() {
        repository.upsert(new PriceForecast(null, propertyId,
                new PriceOutlook(ForecastDirection.FLAT, ForecastConfidence.LOW, 12, List.of(), List.of()),
                ForecastDirection.FLAT, null, null, Instant.now()));

        final var found = repository.findByPropertyId(propertyId).orElseThrow();
        assertThat(found.model()).isNull();
        assertThat(found.promptHash()).isNull();
    }

    @Test
    @DisplayName("덮어써도 이력에는 남는다 — 없으면 사후 검증할 과거가 사라진다 (설계 I138)")
    void keepsHistoryEvenThoughLatestIsOverwritten() {
        // given — 같은 매물에 세 번 전망이 나왔다
        repository.upsert(new PriceForecast(null, propertyId,
                new PriceOutlook(ForecastDirection.UP, ForecastConfidence.HIGH, 12,
                        List.of(new PriceFactor("실거래 추세", ForecastDirection.UP, FactorWeight.HIGH,
                                "직전 3개월 중앙값 11억 → 최근 12억 (+9.1%)")),
                        List.of()),
                ForecastDirection.UP, "hash1", "claude", Instant.parse("2026-01-01T00:00:00Z")));
        repository.upsert(new PriceForecast(null, propertyId,
                new PriceOutlook(ForecastDirection.FLAT, ForecastConfidence.LOW, 12, List.of(), List.of()),
                ForecastDirection.UP, "hash2", "claude", Instant.parse("2026-04-01T00:00:00Z")));
        repository.upsert(new PriceForecast(null, propertyId,
                new PriceOutlook(ForecastDirection.DOWN, ForecastConfidence.MEDIUM, 12, List.of(), List.of()),
                ForecastDirection.DOWN, "hash3", "claude", Instant.parse("2026-07-01T00:00:00Z")));

        // then — 최신은 하나지만
        assertThat(repository.findByPropertyId(propertyId).orElseThrow().promptHash())
                .isEqualTo("hash3");

        // 이력은 셋 다, 최근 것부터
        final List<PriceForecast> history = repository.history(propertyId);
        assertThat(history).hasSize(3);
        assertThat(history.stream().map(PriceForecast::promptHash))
                .containsExactly("hash3", "hash2", "hash1");

        // 가장 오래된 전망의 요인까지 남아 있다 — 왜 그렇게 봤는지가 검증의 재료다
        final PriceForecast oldest = history.getLast();
        assertThat(oldest.outlook().direction()).isEqualTo(ForecastDirection.UP);
        assertThat(oldest.codeDirection()).isEqualTo(ForecastDirection.UP);
        assertThat(oldest.computedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(oldest.outlook().factors()).hasSize(1);
        assertThat(oldest.outlook().factors().getFirst().evidence()).contains("+9.1%");
    }

    @Test
    @DisplayName("이력이 없는 매물은 빈 목록이다")
    void emptyHistoryWhenNever() {
        assertThat(repository.history(propertyId)).isEmpty();
    }

    @Test
    @DisplayName("없는 매물은 비어 있다")
    void emptyWhenAbsent() {
        assertThat(repository.findByPropertyId(999_999L)).isEmpty();
    }
}
