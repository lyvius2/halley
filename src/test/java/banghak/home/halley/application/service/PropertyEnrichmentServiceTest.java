package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.application.port.out.external.HousingPricePort;
import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.geo.PoiResult;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.OfficialPrice;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SchoolSource;
import banghak.home.halley.domain.property.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("매물 등록 후 비동기 보정 (설계 I53 · I54)")
class PropertyEnrichmentServiceTest {

    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final KakaoLocalPort kakaoLocalPort = mock(KakaoLocalPort.class);
    private final HousingPricePort housingPricePort = mock(HousingPricePort.class);
    private final GeoService geoService = mock(GeoService.class);
    private final ReferenceTransactionService referenceTransactionService =
            mock(ReferenceTransactionService.class);

    private final LlmRecommendationService llmRecommendationService = mock(LlmRecommendationService.class);
    private final LandUseService landUseService = mock(LandUseService.class);
    private final ScoringService scoringService = mock(ScoringService.class);

    private final PropertyEnrichmentService service = new PropertyEnrichmentService(
            propertyRepository, kakaoLocalPort, housingPricePort, geoService,
            referenceTransactionService, llmRecommendationService, landUseService, scoringService);

    @Test
    @DisplayName("초등학교가 비어 있으면 카카오 최근접 초등학교로 채우고 출처를 KAKAO로 남긴다")
    void fillsSchoolFromKakao() {
        // given
        givenProperty(property(null, new BigDecimal("84.9"), "102동"));
        when(kakaoLocalPort.searchCategory(anyString(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(
                        PoiResult.of("서울대치중학교", "SC4", 200, "127.0", "37.5"),
                        PoiResult.of("서울대곡초등학교", "SC4", 402, "127.0", "37.5"),
                        PoiResult.of("서울대현초등학교", "SC4", 900, "127.0", "37.5")));

        // when
        service.enrich(1L);

        // then — 중학교는 거르고 더 가까운 초등학교를 고른다. 402m / 67m = 6분
        final Property saved = captureSaved();
        assertThat(saved.schoolName()).isEqualTo("서울대곡초등학교");
        assertThat(saved.schoolWalkMinutes()).isEqualTo(6);
        assertThat(saved.schoolSource()).isEqualTo(SchoolSource.KAKAO);
    }

    @Test
    @DisplayName("붙여넣기로 초등학교가 이미 채워져 있으면 카카오를 부르지 않는다")
    void keepsPastedSchool() {
        // given
        givenProperty(property("서울혜화초등학교", new BigDecimal("84.9"), "102동"));

        // when
        service.enrich(1L);

        // then
        verify(kakaoLocalPort, never()).searchCategory(anyString(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("공시가격은 전용면적이 맞는 건 중에서 고른다 — 같은 단지라도 타입마다 값이 다르다")
    void picksOfficialPriceMatchingArea() {
        // given
        givenProperty(property("서울혜화초등학교", new BigDecimal("84.43"), "27동"));
        givenPnu("1168010600103160000");
        when(housingPricePort.fetchApartmentPrices(anyString())).thenReturn(List.of(
                price(400_000_000L, "27", new BigDecimal("59.90")),
                price(656_000_000L, "27", new BigDecimal("84.43")),
                price(662_000_000L, "27", new BigDecimal("84.43")),
                price(980_000_000L, "27", new BigDecimal("115.50"))));

        // when
        service.enrich(1L);

        // then — 84.43㎡ 두 건의 중앙값. 59.9·115.5는 다른 타입이라 후보에서 빠진다
        final Property saved = captureSaved();
        assertThat(saved.officialPrice()).isEqualTo(662_000_000L);
        assertThat(saved.officialPriceYear()).isEqualTo(2026);
        assertThat(saved.pnu()).isEqualTo("1168010600103160000");
    }

    @Test
    @DisplayName("같은 면적이 여러 동에 있으면 매물의 동을 우선한다 — 공시가격 동명은 숫자만 온다")
    void prefersSameDong() {
        // given — 매물은 `102동`, 공시가격은 `102`
        givenProperty(property("서울혜화초등학교", new BigDecimal("84.43"), "102동 1401호"));
        givenPnu("1168010600103160000");
        when(housingPricePort.fetchApartmentPrices(anyString())).thenReturn(List.of(
                price(656_000_000L, "27", new BigDecimal("84.43")),
                price(700_000_000L, "102", new BigDecimal("84.43"))));

        // when
        service.enrich(1L);

        // then
        assertThat(captureSaved().officialPrice()).isEqualTo(700_000_000L);
    }

    @Test
    @DisplayName("공동주택 결과가 없으면 개별주택(단독·다가구)으로 한 번 더 조회한다")
    void fallsBackToDetachedHouse() {
        // given
        givenProperty(property("서울혜화초등학교", new BigDecimal("147.8"), null));
        givenPnu("1111016700100200000");
        when(housingPricePort.fetchApartmentPrices(anyString())).thenReturn(List.of());
        when(housingPricePort.fetchDetachedHousePrices(anyString()))
                .thenReturn(List.of(price(268_000_000L, null, new BigDecimal("147.8"))));

        // when
        service.enrich(1L);

        // then
        assertThat(captureSaved().officialPrice()).isEqualTo(268_000_000L);
    }

    @Test
    @DisplayName("PNU를 못 만들면 공시가격을 조회하지 않는다")
    void skipsWhenPnuUnresolved() {
        // given — 카카오가 좌표는 주지만 지번(본번)이 없어 PNU가 안 나오는 경우
        givenProperty(property("서울혜화초등학교", new BigDecimal("84.9"), "102동"));
        when(geoService.geocode(anyString())).thenReturn(Optional.of(new GeoSearchResult(
                "서울 종로구 명륜2가", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                "1111014000", null)));

        // when
        service.enrich(1L);

        // then
        verify(housingPricePort, never()).fetchApartmentPrices(anyString());
        verify(propertyRepository, never()).update(any(Property.class));
    }

    @Test
    @DisplayName("실거래가는 상세 모달이 버튼 없이 보여줄 수 있도록 미리 받아 둔다")
    void prefetchesReferenceTrades() {
        // given
        givenProperty(property("서울혜화초등학교", new BigDecimal("84.9"), "102동"));

        // when
        service.enrich(1L);

        // then
        verify(referenceTransactionService).getReferences(1L, null, null);
    }

    private void givenProperty(Property property) {
        when(propertyRepository.findById(1L)).thenReturn(Optional.of(property));
    }

    private void givenPnu(String pnu) {
        when(geoService.geocode(anyString())).thenReturn(Optional.of(new GeoSearchResult(
                "서울 강남구 대치동 316", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                pnu.substring(0, 10), pnu)));
    }

    private Property captureSaved() {
        final ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(propertyRepository).update(captor.capture());
        return captor.getValue();
    }

    private OfficialPrice price(Long won, String dong, BigDecimal areaM2) {
        return new OfficialPrice(won, 2026, dong, "1401", areaM2);
    }

    private Property property(String schoolName, BigDecimal areaExclusiveM2, String dongHo) {
        return new Property(
                1L, "테스트단지", dongHo, DealType.SALE, 1_120_000_000L, 234_762,
                null, "서울 강남구 대치동 316", new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, areaExclusiveM2, null, 14, 20, null, "3/2", "남향",
                1995, null, null, null, 4424, null, null, null,
                null, null, null, null, null,
                schoolName, schoolName == null ? null : 6, schoolName == null ? null : SchoolSource.PASTE,
                null, null, null,
                SourceType.PASTE, null, null, null, null, null,
                false, ListingStatus.ACTIVE, true, null, 0, null,null,null, 1L, Instant.now());
    }
}
