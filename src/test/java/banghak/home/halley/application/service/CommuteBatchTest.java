package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.FloorBand;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.MoveInType;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SourceType;
import banghak.home.halley.domain.scoring.TransitResult;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 통근 조회를 <b>몇 번 부르는가</b> (설계 I217 · I218).
 *
 * <p>운영 로그에 이렇게 찍혔습니다.
 *
 * <pre>
 * LLM transit fallback answered 2 of 2 legs   ← 묶어서 둘 다 받았다
 * LLM transit fallback failed. legs=1         ← 그런데 또 하나씩 물었다
 * LLM transit fallback failed. legs=1
 * </pre>
 *
 * <p>묶어 받은 값을 <b>추정으로 저장</b>하는데, `ensureForUser` 는 추정을
 * "다시 물어볼 것"으로 봅니다([I210]). 그래서 <b>같은 사람을 두 번 물었습니다.</b>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(CommuteBatchTest.StubConfig.class)
@DisplayName("통근 조회 횟수 (설계 I217·I218)")
class CommuteBatchTest {

    @TestConfiguration
    static class StubConfig {

        final AtomicInteger singleCalls = new AtomicInteger();
        final AtomicInteger batchCalls = new AtomicInteger();

        @Bean
        @Primary
        OdsayTransitPort odsayTransitPort() {
            return new OdsayTransitPort() {
                @Override
                public TransitResult findTransit(double sx, double sy, double ex, double ey) {
                    singleCalls.incrementAndGet();
                    return TransitResult.estimated(30, 1, 5, List.of());
                }

                @Override
                public Map<String, TransitResult> findTransitBatch(Map<String, double[]> legs) {
                    batchCalls.incrementAndGet();
                    final Map<String, TransitResult> found = new LinkedHashMap<>();
                    legs.keySet().forEach(key ->
                            found.put(key, TransitResult.estimated(30, 1, 5, List.of())));
                    return found;
                }
            };
        }
    }

    @Autowired private StubConfig stub;
    @Autowired private CommuteDataService commuteDataService;
    @Autowired private UserGroupRepository userGroupRepository;
    @Autowired private UserRepository userRepository;

    private Long groupId;

    @BeforeEach
    void setUp() {
        stub.singleCalls.set(0);
        stub.batchCalls.set(0);
        groupId = userGroupRepository.save(new banghak.home.halley.domain.group.UserGroup(
                null, "통근그룹" + System.nanoTime(), null, null, Instant.now())).id();
    }

    @Test
    @DisplayName("묶어 받은 사람은 다시 묻지 않는다 — 추정이라고 두 번 부르면 안 된다")
    void batchedUsersAreNotAskedAgain() {
        final List<User> users = List.of(worker("직원A"), worker("직원B"), worker("직원C"));

        commuteDataService.ensureCommuteMinutes(property(), users);

        assertThat(stub.batchCalls).hasValue(1);
        // 묶어서 셋 다 받았으니 하나씩 물을 일이 없다
        assertThat(stub.singleCalls).hasValue(0);
    }

    @Test
    @DisplayName("묶어 받은 값이 결과에 그대로 들어간다")
    void batchedValuesReachTheResult() {
        final List<User> users = List.of(worker("결과A"), worker("결과B"));

        final Map<Long, Integer> minutes = commuteDataService.ensureCommuteMinutes(property(), users);

        assertThat(minutes).hasSize(2);
        assertThat(minutes.values()).allMatch(m -> m == 30);
    }

    @Test
    @DisplayName("한 명뿐이면 묶지 않는다 — 묶을 것이 없다")
    void singleUserIsNotBatched() {
        commuteDataService.ensureCommuteMinutes(property(), List.of(worker("혼자")));

        assertThat(stub.batchCalls).hasValue(0);
        assertThat(stub.singleCalls).hasValue(1);
    }

    @Test
    @DisplayName("직장 좌표가 없는 사람은 아예 안 묻는다")
    void usersWithoutWorkplaceAreSkipped() {
        final List<User> users = List.of(worker("있음A"), worker("있음B"), noWorkplace("없음"));

        commuteDataService.ensureCommuteMinutes(property(), users);

        assertThat(stub.batchCalls).hasValue(1);
        assertThat(stub.singleCalls).hasValue(0);
    }

    private Property property() {
return new Property(
                System.nanoTime() % 1_000_000L + 5_000L, "통근매물", null, DealType.SALE,
                500_000_000L, null, "서울시", null,
                new BigDecimal("37.50"), new BigDecimal("127.00"),
                null, null, null, 5, 10, FloorBand.MID, null, null, 2018,
                MoveInType.IMMEDIATE, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                SourceType.MANUAL, null, null, null, null, null, false,
                ListingStatus.ACTIVE, true, null, 0, null,
                groupId, "등록자", 1L, Instant.now());
    }

    private User worker(String nickname) {
        return userRepository.save(new User(
                null, nickname + System.nanoTime(), nickname + System.nanoTime(), groupId, "hash",
                UserRole.MEMBER, "회사", new BigDecimal("37.55"), new BigDecimal("126.98"),
                false, true, 0L, 0L, 0L, true, null, null, Instant.now()));
    }

    private User noWorkplace(String nickname) {
        return userRepository.save(new User(
                null, nickname + System.nanoTime(), nickname + System.nanoTime(), groupId, "hash",
                UserRole.MEMBER, null, null, null,
                false, true, 0L, 0L, 0L, true, null, null, Instant.now()));
    }
}
