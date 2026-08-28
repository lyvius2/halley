package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.CriterionScoreView;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.inbound.web.dto.ScoredPropertyResponse;
import banghak.home.halley.adapter.outbound.persistence.PropertyScoreRepository;
import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.config.exception.InvalidScoreException;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.geo.PoiResult;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.scoring.TransitResult;
import banghak.home.halley.domain.user.UserRole;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("local")
class ScoringServiceTest {

    @TestConfiguration
    static class StubConfig {

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
                    return List.of();
                }

                @Override
                public List<PoiResult> searchKeyword(String query, String categoryGroupCode, double x, double y, int radius) {
                    return List.of();
                }
            };
        }

        @Bean
        @Primary
        OdsayTransitPort odsayTransitPort() {
            return (startX, startY, endX, endY) -> TransitResult.missing();
        }
    }

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private UserService userService;

    @Autowired
    private PropertyScoreRepository propertyScoreRepository;

    @Test
    @DisplayName("매매 목록은 총점 내림차순으로 정렬되고 전세와 분리된다")
    void listSortedAndSeparatedByDealType() {
        // given
        userService.create(new CreateUserRequest(
                "budget", "예산보유자", "budget@example.com", "pw12345!", UserRole.MEMBER, null, null, null, 500_000_000L, 60_000_000L, 0L));
        final PropertyResponse cheap = propertyService.create(request("싼 매물", DealType.SALE, 300_000_000L));
        final PropertyResponse expensive = propertyService.create(request("비싼 매물", DealType.SALE, 800_000_000L));
        final PropertyResponse jeonse = propertyService.create(request("전세 매물", DealType.JEONSE, 400_000_000L));
        scoringService.rescore(cheap.id());
        scoringService.rescore(expensive.id());
        scoringService.rescore(jeonse.id());

        // when
        final List<ScoredPropertyResponse> saleList = scoringService.list(DealType.SALE);
        final List<ScoredPropertyResponse> jeonseList = scoringService.list(DealType.JEONSE);

        // then
        final List<String> saleNames = saleList.stream().map(r -> r.property().name()).toList();
        assertThat(saleNames).contains("싼 매물", "비싼 매물").doesNotContain("전세 매물");
        assertThat(saleNames.indexOf("싼 매물")).isLessThan(saleNames.indexOf("비싼 매물"));
        assertThat(jeonseList).extracting(r -> r.property().name())
                .contains("전세 매물")
                .doesNotContain("싼 매물");
    }

    @Test
    @DisplayName("수동 점수는 자동 점수를 덮어쓴다")
    void manualScoreOverridesAuto() {
        // given
        final PropertyResponse created = propertyService.create(request("수동 매물", DealType.SALE, 400_000_000L));

        // when
        final ScoredPropertyResponse result = scoringService.saveManualScores(
                created.id(), Map.of("PRICE", new BigDecimal("80")));

        // then
        final CriterionScoreView price = result.scores().stream()
                .filter(s -> s.code().equals("PRICE")).findFirst().orElseThrow();
        assertThat(price.manualScore()).isEqualByComparingTo("80");
        assertThat(price.effectiveScore()).isEqualByComparingTo("80");
        assertThat(price.scoreSource()).isEqualTo("MANUAL");
        assertThat(propertyScoreRepository.findByPropertyId(created.id()))
                .filteredOn(s -> s.criterionCode().equals("PRICE"))
                .singleElement()
                .satisfies(s -> assertThat(s.manualScore()).isEqualByComparingTo("80"));
    }

    @Test
    @DisplayName("0~100 범위 밖의 수동 점수는 InvalidScoreException이 발생한다")
    void invalidManualScore() {
        // given
        final PropertyResponse created = propertyService.create(request("오류 매물", DealType.SALE, 400_000_000L));

        // when
        final InvalidScoreException ex = assertThrows(
                InvalidScoreException.class,
                () -> scoringService.saveManualScores(created.id(), Map.of("PRICE", new BigDecimal("150"))));

        // then
        assertThat(ex.getCode()).isEqualTo("INVALID_SCORE");
    }

    private PropertyRequest request(String name, DealType dealType, Long priceDeposit) {
        return new PropertyRequest(
                name, null, dealType, priceDeposit, null, null,
                "서울시 도로명주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
