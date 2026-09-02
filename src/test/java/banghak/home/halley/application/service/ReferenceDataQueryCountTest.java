package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.domain.property.DealType;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기준 정보를 <b>매물 수만큼</b> 다시 읽고 있지 않은가 (설계 I238).
 *
 * <p>{@code criterion} 14행, {@code criterion_weight} 14행 — 작아서 눈에 안 띄지만
 * <b>매물마다</b> 읽으면 목록 한 번에 수십 번입니다. [I124]에서 목록의 N+1을
 * 걷어냈는데, [I199]에서 정렬을 넣으며 <b>제가 다시 만들었습니다.</b>
 *
 * <h4>여기서는 캐시를 꺼 둡니다</h4>
 *
 * <p>캐시를 켠 채로 재면 <b>N+1을 되돌려도 이 테스트가 통과합니다</b> — 실제로
 * 확인했습니다. 담아 둔 것이 쿼리를 가려서, 구조가 멀쩡한지 아닌지를 못 봅니다.
 * 그것이 {@code ADJUST_CACHE.md} 가 "캐시는 증상을 가린다"고 말하는 바로 그것입니다.
 * <b>캐시가 비었을 때의 부하</b>를 재야 의미가 있습니다.
 *
 * <p>담아 둔 뒤의 효과는 {@link ReferenceDataCacheTest} 가 따로 봅니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("기준 정보 조회 횟수 (설계 I238)")
class ReferenceDataQueryCountTest {

    /** 보정이 끼면 배경 스레드의 쿼리까지 세어 수가 흔들린다. */
    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    @Autowired private DSLContext dsl;
    @Autowired private ScoringService scoringService;
    @Autowired private PropertyService propertyService;
    @Autowired private UserGroupRepository userGroupRepository;
    @Autowired private UserRepository userRepository;

    private final AtomicInteger weightQueries = new AtomicInteger();
    private final AtomicInteger criterionQueries = new AtomicInteger();

    @BeforeEach
    void setUp() {
        GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
    }

    @AfterEach
    void tearDown() {
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("매물이 늘어도 기준 정보 조회는 늘지 않는다")
    void referenceReadsDoNotGrowWithProperties() {
        for (int i = 0; i < 6; i++) {
            propertyService.create(request("횟수매물" + i));
        }
        scoringService.list(null);   // 채점을 미리 끝내 둔다 — 그 몫까지 세면 흐려진다

        weightQueries.set(0);
        criterionQueries.set(0);
        attachCounter();
        try {
            final var rows = scoringService.list(null);
            assertThat(rows).hasSizeGreaterThanOrEqualTo(6);
        } finally {
            detachCounter();
        }

        // 목록 한 번에 <b>한 줌</b>이면 된다. 매물 수(6)에 비례하면 안 된다
        assertThat(weightQueries.get())
                .as("criterion_weight 조회 %d회 — 매물 6건", weightQueries.get())
                .isLessThanOrEqualTo(3);
        assertThat(criterionQueries.get())
                .as("criterion 조회 %d회 — 매물 6건", criterionQueries.get())
                .isLessThanOrEqualTo(3);
    }

    /** 쿼리를 가로채 세는 리스너를 잠깐 붙인다. */
    private void attachCounter() {
        dsl.configuration().set(() -> new ExecuteListener() {
            @Override
            public void executeStart(ExecuteContext ctx) {
                final String sql = ctx.sql() == null ? "" : ctx.sql().toLowerCase(Locale.ROOT);
                count(sql, "criterion_weight", weightQueries);
                count(sql, "criterion", criterionQueries);
            }
        });
    }

    /**
     * 표 이름을 <b>정확히</b> 본다.
     *
     * <p>{@code sql.contains("criterion")} 으로 세면 {@code user_criterion_score} 와
     * {@code property_score.criterion_code} 까지 걸립니다 — 고쳤는데도 실패합니다.
     * 실제로 한 번 걸렸습니다.
     */
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

    /**
     * <b>아무것도 담지 않는 캐시.</b>
     *
     * <p>담아 두기가 구조를 가리지 못하게 합니다. 쓰기는 받아 주되 읽기는 늘 빈손이라,
     * 이 테스트가 보는 것은 <b>원본까지 실제로 나가는 쿼리</b>뿐입니다.
     */
    @TestConfiguration
    static class ColdCache {

        @Bean
        @Primary
        CachePort alwaysMisses() {
            return new CachePort() {
                @Override
                public Optional<String> get(String namespace, String key) {
                    return Optional.empty();
                }

                @Override
                public void put(String namespace, String key, String json, Duration ttl) {
                    // 담지 않는다
                }

                @Override
                public void evict(String namespace, String key) {
                    // 담은 것이 없으니 지울 것도 없다
                }

                @Override
                public void evictAll(String namespace) {
                    // 위와 같다
                }
            };
        }
    }
}
