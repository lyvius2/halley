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
                    return List.of(PoiResult.of("POI-" + categoryGroupCode, categoryGroupCode, 100, "127.0", "37.5"));
                }
            };
        }
    }

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
    @DisplayName("최초 조회에서 카테고리별 POI를 수집해 저장하고 이후에는 캐시를 사용한다")
    void cachesAfterFirstFetch() {
        // given
        final Property property = propertyWithCoords("POI 테스트");

        // when
        final List<NearbyFacility> first = poiDataService.ensureNearby(property);
        final List<NearbyFacility> second = poiDataService.ensureNearby(property);

        // then
        assertThat(first).hasSize(10);
        assertThat(second).hasSize(10);
        assertThat(stubConfig.calls.get()).isEqualTo(10);
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
                null, null, null, null, null);
    }
}
