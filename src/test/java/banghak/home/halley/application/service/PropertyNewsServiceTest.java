package banghak.home.halley.application.service;

import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("관련 기사 검색어 (설계 I137)")
class PropertyNewsServiceTest {

    private final PropertyNewsService service = new PropertyNewsService(
            mock(banghak.home.halley.application.port.out.external.NewsSearchPort.class),
            mock(PropertyAccessGuard.class), 10);

    @Test
    @DisplayName("단지명에 지역을 붙인다 — 단지명만이면 동명이 단지가 섞인다")
    void combinesDistrictAndName() {
        assertThat(service.queryOf(property("래미안대치팰리스", "서울 강남구 대치동 316")))
                .isEqualTo("강남구 대치동 래미안대치팰리스");
    }

    @Test
    @DisplayName("지역을 못 뽑으면 단지명만 쓴다")
    void fallsBackToNameOnly() {
        assertThat(service.queryOf(property("래미안대치팰리스", "주소 미상")))
                .isEqualTo("래미안대치팰리스");
    }

    @Test
    @DisplayName("단지명이 없거나 너무 짧으면 검색하지 않는다 — 아무 기사나 걸린다")
    void skipsUnusableNames() {
        assertThat(service.queryOf(property(null, "서울 강남구 대치동 316"))).isNull();
        assertThat(service.queryOf(property("A", "서울 강남구 대치동 316"))).isNull();
        assertThat(service.queryOf(property("  ", "서울 강남구 대치동 316"))).isNull();
    }

    @Test
    @DisplayName("읍·면 주소도 지역으로 뽑는다")
    void handlesEupMyeon() {
        assertThat(service.queryOf(property("동탄역시범호반써밋", "경기도 화성시 동탄면 청계리 525")))
                .isEqualTo("화성시 동탄면 동탄역시범호반써밋");
    }

    private Property property(String name, String addressJibun) {
        return new Property(
                1L, name, null, DealType.SALE, 1_000_000_000L, null,
                null, addressJibun, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, new BigDecimal("84.9"), null, 5, 15, null, null, null,
                2018, null, null, null, 300, null, null, null,
                null, null, null, null, null,
                null, null, null,
                null, null, null,
                SourceType.MANUAL, null, null, null, null, null,
                false, ListingStatus.ACTIVE, true, null, 0, null, null, null, 1L, Instant.now());
    }
}
