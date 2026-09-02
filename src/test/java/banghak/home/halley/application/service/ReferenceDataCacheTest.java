package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.outbound.persistence.CriterionRepository;
import banghak.home.halley.adapter.outbound.persistence.CriterionWeightRepository;
import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.scoring.Criterion;
import banghak.home.halley.domain.scoring.CriterionWeight;
import banghak.home.halley.domain.scoring.ScoringType;
import banghak.home.halley.support.GroupTestSupport;
import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 담아 둔 기준 정보가 <b>줄이는가</b>, 그리고 <b>제때 버려지는가</b> (설계 I239).
 *
 * <p>[I238]에서 N+1을 걷어내 매물 수에 비례하는 조회는 없앴습니다. 그래도
 * <b>요청마다 한 줌</b>은 계속 나갑니다.
 *
 * <p><b>그 한 줌이 얼마나 비싼지는 이제 다릅니다</b>(설계 I242). DB 가 앱과 같은
 * EC2 안 Docker 로 오면서 왕복이 20ms 에서 1ms 아래로 내려갔습니다. 그래도 캐시는
 * 씁니다 — 왕복이 싸졌다고 사람이 안 건드리는 표를 요청마다 다시 물을 이유가
 * 생기지는 않습니다.
 *
 * <p>이 테스트가 보는 것은 <b>이득의 크기가 아니라 약속을 지키는지</b>입니다:
 * 담았으면 안 읽고, 바뀌었으면 버린다.
 *
 * <h4>줄이는 것보다 버리는 것이 중요합니다</h4>
 *
 * <p>담아 두기의 진짜 위험은 느려지는 것이 아니라 <b>틀린 값으로 계산하는 것</b>입니다.
 * 가중치를 바꿔도 한 시간 동안 옛 순위로 총점이 나오고, 규제 파라미터가 그러면
 * <b>대출 한도가 틀립니다.</b> 그래서 쓰는 자리마다 <b>따로</b> 봅니다 —
 * 지우는 곳이 여럿인데 한꺼번에 확인하면 <b>하나만 지워도 통과</b>합니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("기준 정보 캐시 (설계 I239)")
class ReferenceDataCacheTest {

    /** 목록에서 쓰지 않는 순위 — 유일 인덱스에 걸리지 않게 비어 있는 자리를 쓴다 */
    private static final int SPARE_RANK = 99;
    private static final String SPARE_CODE = "CACHE_TEST";

    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    @Autowired private DSLContext dsl;
    @Autowired private ScoringService scoringService;
    @Autowired private PropertyService propertyService;
    @Autowired private CriterionRepository criterionRepository;
    @Autowired private CriterionWeightRepository criterionWeightRepository;
    @Autowired private SystemConfigRepository systemConfigRepository;
    @Autowired private UserGroupRepository userGroupRepository;
    @Autowired private UserRepository userRepository;

    private final AtomicInteger weightQueries = new AtomicInteger();
    private final AtomicInteger criterionQueries = new AtomicInteger();
    private final AtomicInteger configQueries = new AtomicInteger();
    private final AtomicInteger regulationQueries = new AtomicInteger();

    @BeforeEach
    void setUp() {
        GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
    }

    @AfterEach
    void tearDown() {
        criterionWeightRepository.delete(SPARE_CODE);
        criterionRepository.delete(SPARE_CODE);
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("두 번째 목록에서는 기준 정보를 아예 안 읽는다")
    void cachedReferenceDataIsNotReadAgain() {
        for (int i = 0; i < 3; i++) {
            propertyService.create(request("캐시매물" + i));
        }
        scoringService.list(null);   // 여기서 담긴다

        reset();
        attachCounter();
        try {
            scoringService.list(null);
        } finally {
            detachCounter();
        }

        assertThat(criterionQueries.get()).as("criterion").isZero();
        assertThat(weightQueries.get()).as("criterion_weight").isZero();
        assertThat(configQueries.get()).as("system_config").isZero();
        assertThat(regulationQueries.get()).as("regulation_param").isZero();
    }

    @Test
    @DisplayName("채점 항목을 저장하면 담아 둔 것이 버려진다")
    void savingACriterionEvicts() {
        criterionRepository.findAll();   // 담아 둔다

        criterionRepository.save(new Criterion(SPARE_CODE, "캐시 확인용", ScoringType.MANUAL, true));

        assertThat(criterionRepository.findAll())
                .as("방금 넣은 항목이 곧바로 보여야 한다")
                .extracting(Criterion::code)
                .contains(SPARE_CODE);
    }

    @Test
    @DisplayName("채점 항목을 지우면 담아 둔 것이 버려진다")
    void deletingACriterionEvicts() {
        criterionRepository.save(new Criterion(SPARE_CODE, "캐시 확인용", ScoringType.MANUAL, true));
        criterionRepository.findAll();   // 있는 상태로 담아 둔다

        criterionRepository.delete(SPARE_CODE);

        assertThat(criterionRepository.findAll())
                .as("지운 항목이 계속 보이면 채점표에 유령이 남는다")
                .extracting(Criterion::code)
                .doesNotContain(SPARE_CODE);
    }

    @Test
    @DisplayName("가중치를 저장하면 담아 둔 것이 버려진다")
    void savingAWeightEvicts() {
        criterionWeightRepository.findAll();   // 담아 둔다

        criterionWeightRepository.save(
                new CriterionWeight(SPARE_CODE, SPARE_RANK, new BigDecimal("1.50"), null));

        assertThat(criterionWeightRepository.findAll())
                .as("바꾼 가중치가 안 보이면 옛 순위로 총점을 낸다")
                .extracting(CriterionWeight::criterionCode)
                .contains(SPARE_CODE);
    }

    @Test
    @DisplayName("가중치를 지우면 담아 둔 것이 버려진다")
    void deletingAWeightEvicts() {
        criterionWeightRepository.save(
                new CriterionWeight(SPARE_CODE, SPARE_RANK, new BigDecimal("1.50"), null));
        criterionWeightRepository.findAll();   // 있는 상태로 담아 둔다

        criterionWeightRepository.delete(SPARE_CODE);

        assertThat(criterionWeightRepository.findAll())
                .extracting(CriterionWeight::criterionCode)
                .doesNotContain(SPARE_CODE);
    }

    /**
     * 운영 설정은 <b>없는 것도 담습니다</b> — 그래서 지우는 것이 더 중요합니다.
     * 없다고 담아 둔 뒤 새로 넣었는데 안 지우면, 10분 동안 계속 "없다"고 답합니다.
     */
    @Test
    @DisplayName("없다고 담아 둔 설정을 새로 넣으면 곧바로 보인다")
    void savingAConfigEvictsTheNegativeAnswer() {
        final String key = "cache.test.key";
        assertThat(systemConfigRepository.findById(key)).isEmpty();   // "없다"가 담긴다

        systemConfigRepository.save(new banghak.home.halley.domain.setting.SystemConfig(
                key, "값", banghak.home.halley.domain.setting.ConfigValueType.STRING,
                banghak.home.halley.domain.setting.ConfigCategory.SCORING,
                "캐시 확인용", false, null, null));

        assertThat(systemConfigRepository.findById(key))
                .as("없다고 담아 둔 답이 안 버려지면 새 설정이 10분간 무시된다")
                .isPresent();

        systemConfigRepository.delete(key);
    }

    private void reset() {
        weightQueries.set(0);
        criterionQueries.set(0);
        configQueries.set(0);
        regulationQueries.set(0);
    }

    private void attachCounter() {
        dsl.configuration().set(() -> new ExecuteListener() {
            @Override
            public void executeStart(ExecuteContext ctx) {
                final String sql = ctx.sql() == null ? "" : ctx.sql().toLowerCase(Locale.ROOT);
                count(sql, "criterion_weight", weightQueries);
                count(sql, "criterion", criterionQueries);
                count(sql, "system_config", configQueries);
                count(sql, "regulation_param", regulationQueries);
            }
        });
    }

    /** {@code criterion} 은 {@code user_criterion_score} 에도 들어 있다 — 표 이름을 정확히 본다 */
    private void count(String sql, String table, AtomicInteger counter) {
        if (Pattern.compile("\\bfrom\\s+\"?" + table + "\"?(\\s|$|\\))").matcher(sql).find()) {
            counter.incrementAndGet();
        }
    }

    private void detachCounter() {
        dsl.configuration().set(() -> new ExecuteListener() { });
    }

    private PropertyRequest request(String name) {
        return new PropertyRequest(
                name, null, DealType.SALE, 500_000_000L, null,
                "서울시 도로명주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                new BigDecimal("84.9"), new BigDecimal("59.9"), null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
