package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.inbound.web.dto.ReferenceCardResponse;
import banghak.home.halley.application.port.out.external.MinistryReferencePort;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ReferenceTrade;
import org.junit.jupiter.api.DisplayName;
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
                    new ReferenceTrade("다른아파트", 500_000_000L, new BigDecimal("60.0"), 3, LocalDate.of(2026, 6, 5)));
        }
    }

    @Autowired
    private ReferenceTransactionService referenceTransactionService;

    @Autowired
    private PropertyService propertyService;

    @Test
    @DisplayName("단지명이 같은 최근 실거래만 필터링해 참고 카드를 만든다")
    void filtersAndBuildsCard() {
        // given
        final PropertyResponse property = propertyService.create(request("독립문삼호", new BigDecimal("84.98"), 1_500_000_000L));

        // when
        final ReferenceCardResponse card = referenceTransactionService.getReferences(property.id(), "11010", "202607");

        // then
        assertThat(card.transactions()).hasSize(2);
        assertThat(card.transactions().getFirst().price()).isEqualTo(925_000_000L);
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
        assertThat(card.transactions()).hasSize(2);
    }

    private PropertyRequest request(String name, BigDecimal areaExclusiveM2, Long priceDeposit) {
        return new PropertyRequest(
                name, null, DealType.SALE, priceDeposit, null, null,
                "서울시", null, null, null,
                null, areaExclusiveM2, null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
