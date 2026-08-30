package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.CriterionScoreView;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.inbound.web.dto.ScoreVersionResponse;
import banghak.home.halley.adapter.inbound.web.dto.ScoredPropertyResponse;
import banghak.home.halley.adapter.outbound.persistence.LlmRecommendationRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import banghak.home.halley.domain.llm.LlmRecommendation;
import java.time.Instant;
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

    /**
     * 이 테스트는 채점만 본다. 비동기 보정이 같은 매물에 트랜잭션을 잡으면
     * `property_score`에서 락이 겹쳐 엉뚱한 실패가 난다 — 보정 자체는
     * `PropertyEnrichmentServiceTest`가 본다.
     */
    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository groupTestUserRepository;

    /** 매물은 그룹에 딸리므로 그룹에 속한 회원으로 로그인해 둔다 (설계 I87). */
    /** 로그인한 회원의 그룹. 채점 입력(현금 합계·통근)이 이 그룹으로 좁혀진다 (설계 I91). */
    private Long myGroupId;

    @BeforeEach
    void loginAsGroupMember() {
        myGroupId = GroupTestSupport.loginAsGroupMember(userGroupRepository, groupTestUserRepository);
    }

    @AfterEach
    void clearLogin() {
        GroupTestSupport.logout();
    }

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private UserService userService;

    @Autowired
    private PropertyScoreRepository propertyScoreRepository;

    @Autowired
    private LlmRecommendationRepository llmRecommendationRepository;

    @Test
    @DisplayName("매매 목록은 총점 내림차순으로 정렬되고 전세와 분리된다")
    void listSortedAndSeparatedByDealType() {
        // given
        userService.create(new CreateUserRequest(
                "budget", "예산보유자", myGroupId, "pw12345!", UserRole.MEMBER, null, null, null, 500_000_000L, 60_000_000L, 0L));
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
    @DisplayName("AI 추천이 나중에 저장되면 채점이 스스로 다시 계산된다 — 두 화면이 어긋나면 안 된다")
    void healsStaleScoreWhenRecommendationArrivesLater() {
        // given — 등록 시점에 채점된다. 그때는 AI 추천도가 아직 없다
        final PropertyResponse created = propertyService.create(
                request("나중에 AI 붙는 매물", DealType.SALE, 400_000_000L));
        scoringService.rescore(created.id());
        assertThat(llmScoreOf(created.id())).isNull();

        // when — 보정이 뒤늦게 AI 추천을 저장한다 (비동기라 채점보다 늦게 끝난다)
        llmRecommendationRepository.upsert(new LlmRecommendation(
                null, created.id(), new BigDecimal("77.00"), "채광이 좋습니다",
                "test-model", "hash", 1, Instant.now()));

        // then — 다시 채점하라고 시키지 않아도 조회할 때 스스로 맞춘다.
        // 상세 모달은 llm_recommendation을, 채점 모달은 property_score를 읽으므로
        // 여기가 낡으면 같은 매물의 AI 점수가 화면마다 달라진다
        assertThat(llmScoreOf(created.id())).isEqualByComparingTo("77.00");
    }

    private BigDecimal llmScoreOf(Long propertyId) {
        return scoringService.getScored(propertyId).scores().stream()
                .filter(s -> "LLM_RECOMMENDATION".equals(s.code()))
                .findFirst().orElseThrow()
                .effectiveScore();
    }

    @Test
    @DisplayName("채점이 바뀌면 판 번호가 오른다 — 화면이 뒤에서 바뀐 것을 알아채는 유일한 신호")
    void scoreVersionAdvancesOnRescore() {
        // given
        final PropertyResponse created = propertyService.create(
                request("판번호 매물", DealType.SALE, 400_000_000L));
        final long before = scoringService.getScored(created.id()).scoreVersion();

        // when — 사용자가 점수를 수기로 바꾼다
        scoringService.saveManualScores(created.id(), Map.of("PRICE", new BigDecimal("80")));

        // then
        assertThat(scoringService.getScored(created.id()).scoreVersion()).isGreaterThan(before);
    }

    @Test
    @DisplayName("판 번호 목록은 매물마다 한 줄씩 준다 — 목록 전체를 받지 않으려고 있는 것")
    void listsScoreVersionsPerProperty() {
        // given
        final PropertyResponse a = propertyService.create(request("버전A", DealType.SALE, 300_000_000L));
        final PropertyResponse b = propertyService.create(request("버전B", DealType.SALE, 500_000_000L));
        scoringService.rescore(a.id());

        // when
        final List<ScoreVersionResponse> versions = scoringService.scoreVersions();

        // then
        assertThat(versions).extracting(ScoreVersionResponse::propertyId).contains(a.id(), b.id());
        assertThat(versions).filteredOn(v -> v.propertyId().equals(a.id()))
                .singleElement()
                .satisfies(v -> assertThat(v.scoreVersion()).isPositive());
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
                null, null, null);
    }
}
