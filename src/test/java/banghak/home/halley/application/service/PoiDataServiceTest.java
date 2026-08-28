package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.geo.PoiResult;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.domain.property.Property;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class PoiDataServiceTest {

    @TestConfiguration
    static class StubConfig {

        final AtomicInteger calls = new AtomicInteger();

        @Bean
        @Primary
        KakaoLocalPort kakaoLocalPort() {
            return new KakaoLocalPort() {
                @Override
                public List<GeoSearchResult> searchAddress(String query) {
                    return List.of();
                }

                @Override
                public List<PoiResult> searchCategory(String categoryGroupCode, double x, double y, int radius) {
                    calls.incrementAndGet();
                    if ("AT4".equals(categoryGroupCode)) {
                        // AT4에는 산과 함께 테마거리 등 녹지가 아닌 결과가 섞여 온다
                        return List.of(
                                PoiResult.of("불암산", "AT4", 100, "127.0", "37.5", "여행 > 관광,명소 > 산"),
                                PoiResult.of("노원문화의거리", "AT4", 100, "127.0", "37.5", "여행 > 관광,명소 > 테마거리"));
                    }
                    return List.of(PoiResult.of("POI-" + categoryGroupCode, categoryGroupCode, 100, "127.0", "37.5"));
                }

                @Override
                public List<PoiResult> searchKeyword(String query, String categoryGroupCode,
                                                     double x, double y, int radius) {
                    calls.incrementAndGet();
                    final String categoryName = switch (query) {
                        case "공원" -> "여행 > 공원";
                        case "하천" -> "여행 > 관광,명소 > 하천";
                        default -> "여행 > 관광,명소 > 산";
                    };
                    return List.of(PoiResult.of("POI-" + query, categoryGroupCode, 100, "127.0", "37.5", categoryName));
                }
            };
        }
    }

    /**
     * 매물 등록 뒤 도는 비동기 보정(설계 I53)도 카카오를 호출한다. 이 테스트는 POI 수집 횟수를 세므로
     * 보정이 끼어들면 결과가 타이밍에 따라 달라진다 — 여기서는 대상이 아니니 통째로 대체한다.
     */
    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    @Autowired
    private StubConfig stubConfig;

    @Autowired
    private PoiDataService poiDataService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private PropertyRepository propertyRepository;

    @BeforeEach
    void resetCounter() {
        stubConfig.calls.set(0);
    }

    @Test
    @DisplayName("최초 조회에서 카테고리별 POI를 수집해 캐시하고 이후에는 캐시를 사용한다")
    void cachesAfterFirstFetch() {
        // given
        final Property property = propertyWithCoords("POI 테스트");

        // when
        final List<NearbyFacility> first = poiDataService.ensureNearby(property);
        final List<NearbyFacility> second = poiDataService.ensureNearby(property);

        // then — 카테고리 10회 + GREEN 키워드(공원·하천·산) 3회
        assertThat(stubConfig.calls.get()).isEqualTo(13);
        assertThat(first).hasSize(13);
        assertThat(second).hasSize(13);
    }

    @Test
    @DisplayName("GREEN은 category_name으로 공원·산·하천을 분류해 저장하고 녹지가 아닌 결과는 버린다")
    void classifiesGreenAndDropsNonGreen() {
        // given
        final Property property = propertyWithCoords("GREEN 분류 테스트");

        // when
        final List<NearbyFacility> facilities = poiDataService.ensureNearby(property);
        final List<NearbyFacility> green = facilities.stream()
                .filter(f -> "GREEN".equals(f.category()))
                .toList();

        // then
        // 산은 AT4 카테고리와 키워드 두 경로로 들어와 중복될 수 있다 — 분류값이 3종뿐인지 본다
        assertThat(green).extracting(NearbyFacility::subCategory)
                .containsOnly("MOUNTAIN", "PARK", "RIVER");
        assertThat(green).extracting(NearbyFacility::name).contains("불암산");
        assertThat(facilities).extracting(NearbyFacility::name).doesNotContain("노원문화의거리");
    }

    @Test
    @DisplayName("좌표가 없는 매물은 POI를 조회하지 않는다")
    void noCoordinatesSkipsFetch() {
        // given
        final Property property = propertyRepository.findById(
                propertyService.create(request(null, null)).id()).orElseThrow();

        // when
        final List<NearbyFacility> result = poiDataService.ensureNearby(property);

        // then
        assertThat(result).isEmpty();
        assertThat(stubConfig.calls.get()).isZero();
    }

    private Property propertyWithCoords(String name) {
        return propertyRepository.findById(
                propertyService.create(request(new BigDecimal("37.5"), new BigDecimal("127.0"))).id()).orElseThrow();
    }

    private PropertyRequest request(BigDecimal lat, BigDecimal lng) {
        return new PropertyRequest(
                "POI 테스트", null, DealType.SALE, 500_000_000L, null, null,
                "서울시", null, lat, lng,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
