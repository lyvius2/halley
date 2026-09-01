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
    private banghak.home.halley.adapter.outbound.persistence.CriterionWeightRepository criterionWeightRepository;

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
    @DisplayName("저장해도 항목 순서가 그대로다 — 읽는 쪽과 재채점하는 쪽이 같은 순서여야 한다 (설계 I199)")
    void scoreOrderSurvivesSaving() {
        // given — 한 번 읽어 둔 순서
        final PropertyResponse created = propertyService.create(request("순서 매물", DealType.SALE, 400_000_000L));
        final List<String> before = scoringService.getScored(created.id()).scores().stream()
                .map(CriterionScoreView::code)
                .toList();
        assertThat(before).hasSizeGreaterThan(3);

        // when — 저장하면 재채점이 돈다. 여기가 다른 순서를 만들던 자리다
        final List<String> afterSave = scoringService
                .saveManualScores(created.id(), Map.of("COMFORT", new BigDecimal("4")))
                .scores().stream()
                .map(CriterionScoreView::code)
                .toList();

        // then — 저장 직후도, 다시 읽어도 같은 순서다
        assertThat(afterSave).containsExactlyElementsOf(before);
        assertThat(scoringService.getScored(created.id()).scores().stream()
                .map(CriterionScoreView::code).toList())
                .containsExactlyElementsOf(before);
    }

    @Test
    @DisplayName("항목은 가중치 순위대로 온다 — 총점에 크게 물리는 것이 위에 (설계 I199)")
    void scoresComeInPriorityOrder() {
        // given
        final PropertyResponse created = propertyService.create(request("순위 매물", DealType.SALE, 400_000_000L));

        // when
        final List<String> codes = scoringService.getScored(created.id()).scores().stream()
                .map(CriterionScoreView::code)
                .toList();

        // then — criterion_weight 의 priority_rank 오름차순
        final Map<String, Integer> ranks = criterionWeightRepository.findAll().stream()
                .filter(w -> w.priorityRank() != null)
                .collect(java.util.stream.Collectors.toMap(
                        w -> w.criterionCode(), w -> w.priorityRank(), (a, b) -> a));
        final List<String> ranked = codes.stream().filter(ranks::containsKey).toList();
        assertThat(ranked).isSortedAccordingTo(
                java.util.Comparator.comparingInt(ranks::get));
    }

    @Test
    @DisplayName("이미 자동 채점된 AUTO 항목은 수동으로 덮어쓰지 않는다 (설계 I111)")
    void keepsAutoScoreOfAlreadyScoredCriterion() {
        // given — 한 번 채점해 건물 연식에 자동 점수가 들어간 상태
        final PropertyResponse created = propertyService.create(request("자동 매물", DealType.SALE, 400_000_000L));
        scoringService.getScored(created.id());
        final CriterionScoreView before = scoreOf(created.id(), "AGE");
        assertThat(before.autoScore()).isNotNull();

        // when — 화면이 추정값으로 채워 둔 칸을 그대로 되돌려 보낸다
        final ScoredPropertyResponse result = scoringService.saveManualScores(
                created.id(), Map.of("AGE", new BigDecimal("80")));

        // then — 무시한다. 받아들이면 자동 채점이 수동으로 굳고 산출 근거도 사라진다
        final CriterionScoreView age = result.scores().stream()
                .filter(s -> s.code().equals("AGE")).findFirst().orElseThrow();
        assertThat(age.manualScore()).isNull();
        assertThat(age.scoreSource()).isEqualTo("AUTO");
        assertThat(age.effectiveScore()).isEqualByComparingTo(before.autoScore());
    }

    @Test
    @DisplayName("자동 산출에 실패한 AUTO 항목은 사람이 채울 수 있다 — 아니면 영영 빈칸이다 (설계 I111)")
    void allowsManualScoreWhenAutoScoreMissing() {
        // given — 채점은 돌았지만 가격은 현금 예산이 없어 미산출로 떨어진 상태
        final PropertyResponse created = propertyService.create(request("미산출 매물", DealType.SALE, 400_000_000L));
        scoringService.getScored(created.id());
        assertThat(scoreOf(created.id(), "PRICE").autoScore()).isNull();

        // when
        final ScoredPropertyResponse result = scoringService.saveManualScores(
                created.id(), Map.of("PRICE", new BigDecimal("80")));

        // then
        final CriterionScoreView price = result.scores().stream()
                .filter(s -> s.code().equals("PRICE")).findFirst().orElseThrow();
        assertThat(price.manualScore()).isEqualByComparingTo("80");
        assertThat(price.scoreSource()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("쾌적함을 저장해도 다른 항목의 자동 채점과 산출 근거는 그대로다 (설계 I111)")
    void savingComfortLeavesOtherCriteriaUntouched() {
        // given
        final PropertyResponse created = propertyService.create(request("쾌적함 매물", DealType.SALE, 400_000_000L));
        scoringService.getScored(created.id());
        final CriterionScoreView ageBefore = scoreOf(created.id(), "AGE");
        assertThat(ageBefore.explanation()).isNotNull();

        // when — 실제로 사람이 고친 것은 쾌적함 하나뿐이다
        scoringService.saveManualScores(created.id(), Map.of("COMFORT", new BigDecimal("4")));

        // then — 이게 무너져서 채점 전체가 수동으로 바뀌고 근거가 사라졌었다
        final CriterionScoreView ageAfter = scoreOf(created.id(), "AGE");
        assertThat(ageAfter.scoreSource()).isEqualTo("AUTO");
        assertThat(ageAfter.manualScore()).isNull();
        assertThat(ageAfter.explanation()).isEqualTo(ageBefore.explanation());
        assertThat(scoreOf(created.id(), "COMFORT").effectiveScore()).isNotNull();
    }

    private CriterionScoreView scoreOf(Long propertyId, String code) {
        return scoringService.getScored(propertyId).scores().stream()
                .filter(s -> code.equals(s.code())).findFirst().orElseThrow();
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
                name, null, dealType, priceDeposit, null,
                "서울시 도로명주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
