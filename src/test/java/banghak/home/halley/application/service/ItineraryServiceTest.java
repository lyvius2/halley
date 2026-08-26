package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryRequest;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.application.port.out.external.KakaoDirectionsPort;
import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.domain.itinerary.DriveRoute;
import banghak.home.halley.domain.itinerary.TravelMode;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.scoring.TransitResult;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class ItineraryServiceTest {

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        KakaoDirectionsPort kakaoDirectionsPort() {
            return (fromLng, fromLat, toLng, toLat) -> new DriveRoute(10, 1000);
        }

        @Bean
        @Primary
        OdsayTransitPort odsayTransitPort() {
            return (sx, sy, ex, ey) -> new TransitResult(15, 1, 5);
        }
    }

    @Autowired
    private ItineraryService itineraryService;

    @Autowired
    private PropertyService propertyService;

    @Test
    @DisplayName("자가용으로 최적 순서와 총 소요시간을 계산한다")
    void optimizeDriving() {
        // given
        final List<Long> ids = List.of(
                propertyService.create(request("임장A")).id(),
                propertyService.create(request("임장B")).id(),
                propertyService.create(request("임장C")).id());

        // when
        final OptimizeItineraryResponse result = itineraryService.optimize(new OptimizeItineraryRequest(
                ids, TravelMode.DRIVING, new BigDecimal("37.5"), new BigDecimal("126.9")));

        // then — 3개 매물 + 출발지 → 이동 3회 × 10분
        assertThat(result.orderedPropertyIds()).hasSize(3);
        assertThat(result.orderedPropertyIds()).containsAll(ids);
        assertThat(result.totalMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("대중교통 모드로 최적 순서를 계산한다")
    void optimizeTransit() {
        // given
        final List<Long> ids = List.of(
                propertyService.create(request("버스A")).id(),
                propertyService.create(request("버스B")).id());

        // when
        final OptimizeItineraryResponse result = itineraryService.optimize(new OptimizeItineraryRequest(
                ids, TravelMode.TRANSIT, new BigDecimal("37.5"), new BigDecimal("126.9")));

        // then — 2개 매물 → 이동 2회 × 15분
        assertThat(result.orderedPropertyIds()).hasSize(2);
        assertThat(result.totalMinutes()).isEqualTo(30);
    }

    private PropertyRequest request(String name) {
        return new PropertyRequest(
                name, null, DealType.SALE, 500_000_000L, null, null,
                "서울시", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null,
                null, null, null);
    }
}
