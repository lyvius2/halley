package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.inbound.web.dto.ReferenceCardResponse;
import banghak.home.halley.adapter.outbound.persistence.LegalDongCodeRepository;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.external.MinistryReferencePort;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.geo.LegalDongCode;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>"받아 오는 중"이 끝나기는 하는가</b> (설계 I262).
 *
 * <p>[I259]에서 화면이 기다리지 않고 다시 묻게 만들면서, 서버가 <b>저장된 것이
 * 없으면 늘</b> "받아 오는 중"이라고 답하게 두었습니다. 못 찾은 매물은 저장될 것이
 * 영영 없으므로 <b>프로그래스바가 한 시간을 돌았습니다.</b>
 *
 * <p>못 찾았다는 사실은 이미 {@code REFERENCE_MISS} 에 남아 있었습니다 — 이 길만
 * 그것을 안 봤을 뿐입니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("배경 조회 표시 (설계 I262)")
class ReferenceLookingTest {

    /** 등록 직후 배경 보정이 같은 조회를 돌리면 호출 수가 흔들린다. */
    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    /** 이 매물과 <b>맞는 거래가 하나도 없는</b> 응답. 그래도 응답은 정상이다 */
    static final AtomicInteger CALLS = new AtomicInteger();
    /** 세워 두면 국토부 호출이 <b>여기서 멈춰 선다</b> — "지금 받아 오는 중"을 만든다. */
    static volatile CountDownLatch HOLD;

    @TestConfiguration
    static class NoMatchConfig {

        @Bean
        @Primary
        MinistryReferencePort ministryReferencePort() {
            return (lawdCd, dealYmd) -> {
                CALLS.incrementAndGet();
                final CountDownLatch hold = HOLD;
                if (hold != null) {
                    try {
                        hold.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return List.of(new ReferenceTrade(
                        "남의단지", 500_000_000L, new BigDecimal("60.0"), 3, LocalDate.of(2026, 6, 5)));
            };
        }
    }

    @Autowired private ReferenceTransactionService referenceTransactionService;
    @Autowired private PropertyService propertyService;
    @Autowired private UserGroupRepository userGroupRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LegalDongCodeRepository legalDongCodeRepository;

    @BeforeEach
    void setUp() {
        CALLS.set(0);
        HOLD = null;
        // 사전이 없으면 국토부를 <b>부르기도 전에</b> 되돌아선다 — 그러면 이 테스트가
        // 재려는 것(중복 호출)을 못 잰다. 정릉동 하나만 넣어 둔다
        if (legalDongCodeRepository.findBySigunguAndDong("성북구", "정릉동").isEmpty()) {
            legalDongCodeRepository.save(new LegalDongCode(
                    "1129013500", "서울특별시", "성북구", "정릉동", null, true, Instant.now()));
        }
        GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
    }

    @AfterEach
    void tearDown() {
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("찾아봤는데 없었으면 더 이상 '받아 오는 중'이라고 하지 않는다")
    void stopsLookingOnceTheSearchIsDone() {
        // given — 등록하고, 배경 조회를 <b>끝까지</b> 돌린다 (실패 아님, 맞는 게 없을 뿐)
        final PropertyResponse property = propertyService.create(request("한화포레나정릉"));
        referenceTransactionService.prefetch(property.id());

        // when — 화면이 3초 뒤 다시 묻는다
        final ReferenceCardResponse card =
                referenceTransactionService.getReferences(property.id(), null, null);

        // then — 끝난 일이다. 여기서 true면 화면은 <b>영원히</b> 돈다
        assertThat(card.looking())
                .as("이미 찾아봤는데 또 '받아 오는 중'이라고 하면 프로그래스바가 안 멈춘다")
                .isFalse();
    }

    @Test
    @DisplayName("화면이 다시 물어도 국토부를 또 부르지 않는다")
    void doesNotRefetchWhileTheScreenKeepsAsking() {
        // given
        final PropertyResponse property = propertyService.create(request("한화포레나정릉"));
        referenceTransactionService.prefetch(property.id());
        final int afterFirst = CALLS.get();
        assertThat(afterFirst).as("첫 조회는 실제로 나갔어야 한다").isGreaterThan(0);

        // when — 화면이 스무 번 다시 묻는다 (REF_POLL_MAX_ATTEMPTS)
        for (int i = 0; i < 20; i++) {
            referenceTransactionService.getReferences(property.id(), null, null);
        }

        // then — 12개월치를 스무 벌 더 받아 오면 안 된다
        assertThat(CALLS.get())
                .as("폴링 스무 번에 국토부 호출이 %d회 늘었다", CALLS.get() - afterFirst)
                .isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("받아 오는 중에 다시 물어도 조회를 또 띄우지 않는다")
    void doesNotStartASecondFetchWhileOneIsRunning() throws Exception {
        // given — 국토부가 응답하지 않는 상태로 붙잡아 둔다
        HOLD = new CountDownLatch(1);
        final PropertyResponse property = propertyService.create(request("한화포레나정릉"));

        try {
            // when — 화면이 처음 열리고, 3초마다 스무 번 다시 묻는다
            referenceTransactionService.getReferences(property.id(), null, null);
            waitUntilFetchStarted();
            for (int i = 0; i < 20; i++) {
                referenceTransactionService.getReferences(property.id(), null, null);
            }

            // then — <b>도는 것은 하나뿐</b>이어야 한다.
            // 여기가 20을 넘으면 화면 하나가 국토부를 240번 두드린 것이다
            assertThat(CALLS.get())
                    .as("받아 오는 중에 스무 번 물었더니 국토부 호출이 %d회 시작됐다", CALLS.get())
                    .isEqualTo(1);
        } finally {
            HOLD.countDown();
        }
    }

    @Test
    @DisplayName("배경 조회가 끝나기를 기다리지 않고 답한다")
    void answersWithoutWaitingForTheFetch() throws Exception {
        // given — 국토부가 10초를 안 돌려준다
        HOLD = new CountDownLatch(1);
        final PropertyResponse property = propertyService.create(request("한화포레나정릉"));

        try {
            // when
            final long startedAt = System.nanoTime();
            final ReferenceCardResponse card =
                    referenceTransactionService.getReferences(property.id(), null, null);
            final long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

            // then — [I259]는 기다리지 않겠다고 했는데 <b>기다리는 함수</b>를 불렀다
            assertThat(card.looking()).isTrue();
            assertThat(elapsedMs)
                    .as("배경 조회를 기다리느라 %dms 붙잡혔다", elapsedMs)
                    .isLessThan(2_000);
        } finally {
            HOLD.countDown();
        }
    }

    @Test
    @DisplayName("법정동코드를 못 찾아 되돌아선 것도 '끝난 것'이다")
    void stopsLookingWhenTheAddressCannotBeResolved() {
        // given — 사전에 없는 동. 카카오 키도 없어 코드를 만들 길이 없다
        final PropertyRequest noSuchDong = new PropertyRequest(
                "묵동어딘가", null, DealType.SALE, 800_000_000L, null,
                null, "서울시 중랑구 묵동 200", null, null,
                null, new BigDecimal("84.9"), null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
        final PropertyResponse property = propertyService.create(noSuchDong);
        referenceTransactionService.prefetch(property.id());

        // when
        final ReferenceCardResponse card =
                referenceTransactionService.getReferences(property.id(), null, null);

        // then — 국토부에 물어보지도 못했지만 <b>끝난 것은 끝난 것</b>이다.
        // 자국을 안 남기면 화면은 영원히 "받아 오는 중"이라고 말한다
        assertThat(CALLS.get()).as("코드가 없으면 국토부를 부르지 않는다").isZero();
        assertThat(card.looking())
                .as("물어볼 주소를 못 만들었는데 '받아 오는 중'이라고 하면 안 멈춘다")
                .isFalse();
    }

    /** 배경 스레드가 국토부까지 실제로 갔는지 확인하고 나서 다시 묻는다. */
    private void waitUntilFetchStarted() throws InterruptedException {
        for (int i = 0; i < 100 && CALLS.get() == 0; i++) {
            Thread.sleep(20);
        }
        assertThat(CALLS.get()).as("배경 조회가 시작되지 않았다").isEqualTo(1);
    }

    private PropertyRequest request(String name) {
        return new PropertyRequest(
                name, null, DealType.SALE, 800_000_000L, null,
                null, "서울시 성북구 정릉동 1037", null, null,
                null, new BigDecimal("84.9"), null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
