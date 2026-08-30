package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.RegulatedAreaRepository;
import banghak.home.halley.domain.loan.RegulatedArea;
import banghak.home.halley.domain.loan.RegulationZone;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("규제지역 판정 (설계 I66)")
class RegulatedAreaServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 29);
    /** 강남구 대치동 = 1168010600. 앞 5자리가 시군구(11680). */
    private static final String PNU = "1168010600103160000";

    private final RegulatedAreaRepository repository = mock(RegulatedAreaRepository.class);
    private final LegalDongCodeService legalDongCodeService = mock(LegalDongCodeService.class);
    private final RegulatedAreaService service = new RegulatedAreaService(repository, legalDongCodeService);

    @Test
    @DisplayName("PNU 앞자리로 지정을 찾는다 — 외부를 부르지 않는다")
    void resolvesFromPnuWithoutCallingKakao() {
        // given
        when(repository.findByCodePrefixes(anyList())).thenReturn(List.of(
                area("11680", RegulationZone.SPECULATION_OVERHEATED, LocalDate.of(2025, 1, 1), null)));

        // when
        final RegulationZone zone = service.resolve(property(PNU, "서울 강남구 대치동 316"), TODAY);

        // then
        assertThat(zone).isEqualTo(RegulationZone.SPECULATION_OVERHEATED);
        verify(legalDongCodeService, never()).deriveSigunguCode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("PNU가 없으면 지번주소로 시군구 코드를 역매핑한다")
    void fallsBackToAddressLookup() {
        // given
        when(legalDongCodeService.deriveSigunguCode("서울 강남구 대치동 316"))
                .thenReturn(java.util.Optional.of("11680"));
        when(repository.findByCodePrefixes(List.of("11680"))).thenReturn(List.of(
                area("11680", RegulationZone.ADJUSTMENT_TARGET, LocalDate.of(2025, 1, 1), null)));

        // when
        final RegulationZone zone = service.resolve(property(null, "서울 강남구 대치동 316"), TODAY);

        // then
        assertThat(zone).isEqualTo(RegulationZone.ADJUSTMENT_TARGET);
    }

    @Test
    @DisplayName("지정 정보가 없으면 비규제로 본다 — 기본값을 규제로 두면 전국이 규제가 된다")
    void defaultsToNormal() {
        // given
        when(repository.findByCodePrefixes(anyList())).thenReturn(List.of());

        // when
        final RegulationZone zone = service.resolve(property(PNU, "서울 강남구 대치동 316"), TODAY);

        // then
        assertThat(zone).isEqualTo(RegulationZone.NORMAL);
    }

    @Test
    @DisplayName("해제된 지정은 무시한다")
    void ignoresReleasedDesignation() {
        // given — 작년에 해제됐다
        when(repository.findByCodePrefixes(anyList())).thenReturn(List.of(
                area("11680", RegulationZone.SPECULATION_OVERHEATED,
                        LocalDate.of(2020, 1, 1), LocalDate.of(2025, 6, 1))));

        // when
        final RegulationZone zone = service.resolve(property(PNU, "서울 강남구 대치동 316"), TODAY);

        // then
        assertThat(zone).isEqualTo(RegulationZone.NORMAL);
    }

    @Test
    @DisplayName("아직 지정일 전이면 적용하지 않는다")
    void ignoresFutureDesignation() {
        // given
        when(repository.findByCodePrefixes(anyList())).thenReturn(List.of(
                area("11680", RegulationZone.ADJUSTMENT_TARGET, LocalDate.of(2027, 1, 1), null)));

        // when
        final RegulationZone zone = service.resolve(property(PNU, "서울 강남구 대치동 316"), TODAY);

        // then
        assertThat(zone).isEqualTo(RegulationZone.NORMAL);
    }

    @Test
    @DisplayName("시군구와 법정동 지정이 겹치면 강한 쪽을 따른다")
    void strongerZoneWinsWhenOverlapping() {
        // given — 시군구는 조정대상, 그 안의 법정동은 투기과열
        when(repository.findByCodePrefixes(anyList())).thenReturn(List.of(
                area("11680", RegulationZone.ADJUSTMENT_TARGET, LocalDate.of(2025, 1, 1), null),
                area("1168010600", RegulationZone.SPECULATION_OVERHEATED, LocalDate.of(2025, 3, 1), null)));

        // when
        final RegulationZone zone = service.resolve(property(PNU, "서울 강남구 대치동 316"), TODAY);

        // then
        assertThat(zone).isEqualTo(RegulationZone.SPECULATION_OVERHEATED);
    }

    @Test
    @DisplayName("주소도 PNU도 없으면 비규제다")
    void noAddressMeansNormal() {
        // when
        final RegulationZone zone = service.resolve(property(null, null), TODAY);

        // then
        assertThat(zone).isEqualTo(RegulationZone.NORMAL);
    }

    private RegulatedArea area(String prefix, RegulationZone zone, LocalDate from, LocalDate to) {
        return new RegulatedArea(1L, prefix, zone, "테스트 지역", from, to, "고시 제0000호", Instant.now());
    }

    private Property property(String pnu, String addressJibun) {
        return new Property(
                1L, "테스트", null, DealType.SALE, 800_000_000L, null,
                null, addressJibun, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, new BigDecimal("84.9"), null, 5, 20, null, null, null,
                2010, null, null, null, 400, null, null, null,
                null, null, null, null, null,
                null, null, null, pnu, null, null,
                SourceType.MANUAL, null, null, null, null, null,
                false, ListingStatus.ACTIVE, true, null, 0, null,null,null, 1L, Instant.now());
    }
}
