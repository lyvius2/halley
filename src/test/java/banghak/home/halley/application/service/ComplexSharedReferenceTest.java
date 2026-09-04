package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.inbound.web.dto.ReferenceCardResponse;
import banghak.home.halley.adapter.outbound.persistence.LegalDongCodeRepository;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.external.MinistryReferencePort;
import banghak.home.halley.domain.geo.LegalDongCode;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.ReferenceTrade;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 단지 같은 평형이면 <b>국토부를 한 번만</b> 부른다 (설계 I266).
 *
 * <p>102동과 104동은 지금까지 <b>아무 관계도 없는 남</b>이었습니다. 매물마다
 * 12개월치를 따로 받았습니다 — 같은 단지 같은 84.9㎡인데도요.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("단지 단위 실거래 (설계 I266)")
class ComplexSharedReferenceTest {

    /** 등록 직후 배경 보정이 같은 조회를 돌리면 호출 수가 흔들린다. */
    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    static final AtomicInteger CALLS = new AtomicInteger();
    /** 시험마다 다른 단지를 쓴다 — 캐시가 이제 단지에 붙으므로 섞이면 못 잰다. */
    private static final AtomicInteger SEQ = new AtomicInteger();

    private String complexName;
    /** 국토부가 돌려줄 단지명. 우리 매물과 다르게 두면 <b>헛걸음</b>이 된다. */
    static volatile String NAME_IN_TRADES = "포레나정릉";

    @TestConfiguration
    static class Ministry {

        @Bean
        @Primary
        MinistryReferencePort ministryReferencePort() {
            return (lawdCd, dealYmd) -> {
                CALLS.incrementAndGet();
                return List.of(new ReferenceTrade(
                        NAME_IN_TRADES, 900_000_000L, new BigDecimal("84.93"), 12,
                        LocalDate.of(2026, 7, 12)));
            };
        }
    }

    @Autowired private ReferenceTransactionService referenceTransactionService;
    @Autowired private ComplexService complexService;
    @Autowired private PropertyService propertyService;
    @Autowired private UserGroupRepository userGroupRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LegalDongCodeRepository legalDongCodeRepository;

    @BeforeEach
    void setUp() {
        CALLS.set(0);
        final int seq = SEQ.incrementAndGet();
        complexName = "포레나정릉" + seq;
        // 국토부 쪽 이름도 같이 맞춘다 — 맞는 것이 나와야 하는 시험이 기본이다
        NAME_IN_TRADES = complexName;
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
    @DisplayName("102동과 104동은 같은 단지다")
    void sameComplexForDifferentBuildings() {
        final PropertyResponse dong102 = propertyService.create(request("102동", "84.9"));
        final PropertyResponse dong104 = propertyService.create(request("104동", "84.9"));

        assertThat(complexService.find(property(dong102)).orElseThrow().id())
                .as("같은 이름·같은 번지인데 단지가 갈렸다")
                .isEqualTo(complexService.find(property(dong104)).orElseThrow().id());
    }

    @Test
    @DisplayName("같은 단지 같은 평형이면 국토부를 다시 부르지 않는다")
    void reusesTradesAcrossPropertiesInTheSameComplex() {
        // given — 102동이 먼저 받아 둔다
        final PropertyResponse dong102 = propertyService.create(request("102동", "84.9"));
        referenceTransactionService.prefetch(dong102.id());
        final int afterFirst = CALLS.get();
        assertThat(afterFirst).as("첫 매물은 실제로 받아 와야 한다").isGreaterThan(0);

        // when — 104동을 새로 등록하고 상세를 연다
        final PropertyResponse dong104 = propertyService.create(request("104동", "84.9"));
        final ReferenceCardResponse card =
                referenceTransactionService.getReferences(dong104.id(), null, null);

        // then — 이미 단지에 붙어 있다. 다시 부를 이유가 없다
        assertThat(card.transactions())
                .as("같은 단지 같은 평형인데 104동은 빈손이다")
                .isNotEmpty();
        assertThat(CALLS.get())
                .as("국토부를 %d회 더 불렀다", CALLS.get() - afterFirst)
                .isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("헛걸음도 단지가 나눠 쓴다 — 같은 단지를 또 훑지 않는다")
    void sharesTheMissAcrossTheComplex() {
        // given — 이 단지에 맞는 거래가 없다. 102동이 12개월을 헛되이 훑는다
        NAME_IN_TRADES = "남의단지";
        final PropertyResponse dong102 = propertyService.create(request("102동", "84.9"));
        referenceTransactionService.prefetch(dong102.id());
        final int afterFirst = CALLS.get();
        assertThat(afterFirst).as("첫 매물은 실제로 훑어야 한다").isGreaterThan(0);

        // when — 같은 단지 같은 평형인 104동을 새로 등록하고 상세를 연다
        final PropertyResponse dong104 = propertyService.create(request("104동", "84.9"));
        referenceTransactionService.getReferences(dong104.id(), null, null);
        sleepBriefly();

        // then — 알아낸 것은 <b>단지의 성질</b>이지 매물의 성질이 아니다.
        // 매물마다 기억하면 새 매물을 넣을 때마다 12개월을 처음부터 다시 훑는다
        assertThat(CALLS.get())
                .as("같은 단지를 %d회 더 훑었다", CALLS.get() - afterFirst)
                .isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("같은 단지라도 평형이 다르면 따로 받는다")
    void differentAreaStillFetches() {
        final PropertyResponse big = propertyService.create(request("102동", "114.9"));
        referenceTransactionService.prefetch(big.id());
        final int afterFirst = CALLS.get();

        final PropertyResponse small = propertyService.create(request("104동", "59.9"));
        referenceTransactionService.getReferences(small.id(), null, null);

        // 조회는 배경에서 돈다 (설계 I262) — 재기 전에 끝나 있지 않다
        // 114.9㎡ 거래를 59.9㎡ 매물의 참고 시세로 쓰면 담보가치가 틀어진다 (설계 I65)
        assertThat(waitForCallsAbove(afterFirst))
                .as("평형이 다른데 앞 매물 것을 그대로 썼다 — 국토부 호출이 %d 그대로다", CALLS.get())
                .isTrue();
    }

    /** 배경 조회가 <b>안 나가는 것</b>을 재려면 나갈 틈은 줘야 한다. */
    private void sleepBriefly() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 배경 조회가 실제로 나갔는지 잠깐 기다려 본다. */
    private boolean waitForCallsAbove(int before) {
        for (int i = 0; i < 100; i++) {
            if (CALLS.get() > before) {
                return true;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private Property property(PropertyResponse response) {
        return new Property(
                response.id(), response.name(), null, null, null, null,
                null, response.addressJibun(), null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, false, null, true,
                null, 0, null, null, null, null, null);
    }

    private PropertyRequest request(String dongHo, String area) {
        return new PropertyRequest(
                complexName, dongHo, DealType.SALE, 800_000_000L, null,
                null, "서울시 성북구 정릉동 1037", null, null,
                null, new BigDecimal(area), null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
