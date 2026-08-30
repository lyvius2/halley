package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.inbound.web.dto.ReferenceCardResponse;
import banghak.home.halley.adapter.inbound.web.dto.ReferenceTransactionResponse;
import banghak.home.halley.application.port.out.external.MinistryReferencePort;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ReferenceTrade;
import org.junit.jupiter.api.DisplayName;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class ReferenceTransactionServiceTest {

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        MinistryReferencePort ministryReferencePort() {
            return (lawdCd, dealYmd) -> List.of(
                    new ReferenceTrade("독립문삼호", 925_000_000L, new BigDecimal("84.93"), 7, LocalDate.of(2026, 7, 12)),
                    new ReferenceTrade("독립문삼호", 1_440_000_000L, new BigDecimal("113.04"), 12, LocalDate.of(2026, 6, 20)),
                    new ReferenceTrade("다른아파트", 500_000_000L, new BigDecimal("60.0"), 3, LocalDate.of(2026, 6, 5)),
                    // 면적은 같지만 단지가 다르다 — 예전 규칙은 이걸 받아들여 값을 왜곡했다 (설계 I71)
                    new ReferenceTrade("옆단지자이", 1_800_000_000L, new BigDecimal("84.90"), 9, LocalDate.of(2026, 7, 20)));
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
    private ReferenceTransactionService referenceTransactionService;

    @Autowired
    private PropertyService propertyService;

    @Test
    @DisplayName("같은 단지라도 전용면적이 다르면 뺀다 — 담보가치가 왜곡된다 (설계 I65)")
    void filtersAndBuildsCard() {
        // given
        final PropertyResponse property = propertyService.create(request("독립문삼호", new BigDecimal("84.98"), 1_500_000_000L));

        // when
        final ReferenceCardResponse card = referenceTransactionService.getReferences(property.id(), "11010", "202607");

        // then — 84.98㎡ 매물에는 84.93㎡만 남는다.
        // 같은 '독립문삼호'라도 113.04㎡는 ±15%를 벗어나므로 뺀다. 예전에는 단지명만 같으면
        // 면적을 보지 않고 받아들여, 이 값들로 담보가치를 매기면 크게 틀어졌다
        assertThat(card.transactions()).hasSize(1);
        assertThat(card.transactions().getFirst().price()).isEqualTo(925_000_000L);
        assertThat(card.transactions()).extracting(ReferenceTransactionResponse::price)
                .doesNotContain(1_440_000_000L);
        assertThat(card.askingPrice()).isEqualTo(1_500_000_000L);
        assertThat(card.gapPercent()).isEqualByComparingTo("62.2");
    }

    @Test
    @DisplayName("이미 캐시된 실거래가 있으면 재조회하지 않고 반환한다")
    void usesCache() {
        // given
        final PropertyResponse property = propertyService.create(request("독립문삼호", new BigDecimal("84.98"), 1_500_000_000L));
        referenceTransactionService.getReferences(property.id(), "11010", "202607");

        // when — 파라미터 없이 호출해도 캐시가 반환된다
        final ReferenceCardResponse card = referenceTransactionService.getReferences(property.id(), null, null);

        // then
        assertThat(card.transactions()).hasSize(1);
    }

    @Test
    @DisplayName("면적이 같아도 다른 단지면 뺀다 — 남의 단지 가격이 섞이면 담보가치가 틀어진다 (설계 I71)")
    void excludesOtherComplexWithSameArea() {
        // given — 84.98㎡ 매물. 스텁에는 같은 면적의 '옆단지자이' 18억 거래가 있다
        final PropertyResponse property = propertyService.create(
                request("독립문삼호", new BigDecimal("84.98"), 1_500_000_000L));

        // when
        final ReferenceCardResponse card =
                referenceTransactionService.getReferences(property.id(), "11010", "202607");

        // then — 독립문삼호 84.93㎡ 한 건만 남는다
        assertThat(card.transactions()).hasSize(1);
        assertThat(card.transactions()).extracting(ReferenceTransactionResponse::price)
                .doesNotContain(1_800_000_000L);
    }

    @Test
    @DisplayName("단지명 표기가 달라도 같은 단지로 본다 — 괄호·'아파트'·공백은 걷어낸다")
    void toleratesComplexNameVariations() {
        // given — 매물명에 접미사가 붙어 있다
        final PropertyResponse property = propertyService.create(
                request("독립문삼호아파트(테스트)", new BigDecimal("84.98"), 1_500_000_000L));

        // when
        final ReferenceCardResponse card =
                referenceTransactionService.getReferences(property.id(), "11010", "202607");

        // then — '독립문삼호'와 같은 단지로 인식된다
        assertThat(card.transactions()).hasSize(1);
        assertThat(card.transactions().getFirst().price()).isEqualTo(925_000_000L);
    }

    @Test
    @DisplayName("어느 단지와도 이름이 맞지 않으면 참고 카드가 빈다 — 틀린 값을 보여주지 않는다")
    void emptyRatherThanWrong() {
        // given — 스텁에 없는 단지
        final PropertyResponse property = propertyService.create(
                request("없는단지", new BigDecimal("84.98"), 1_500_000_000L));

        // when
        final ReferenceCardResponse card =
                referenceTransactionService.getReferences(property.id(), "11010", "202607");

        // then
        assertThat(card.transactions()).isEmpty();
        assertThat(card.gapPercent()).isNull();
    }

    private PropertyRequest request(String name, BigDecimal areaExclusiveM2, Long priceDeposit) {
        return new PropertyRequest(
                name, null, DealType.SALE, priceDeposit, null,
                "서울시", null, null, null,
                null, areaExclusiveM2, null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
