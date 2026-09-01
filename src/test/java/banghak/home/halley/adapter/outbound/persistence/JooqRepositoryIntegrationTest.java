package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.adapter.outbound.persistence.AgentRepository;
import banghak.home.halley.adapter.outbound.persistence.CommuteResultRepository;
import banghak.home.halley.adapter.outbound.persistence.CriterionRepository;
import banghak.home.halley.adapter.outbound.persistence.CriterionWeightRepository;
import banghak.home.halley.adapter.outbound.persistence.LegalDongCodeRepository;
import banghak.home.halley.adapter.outbound.persistence.LoanEstimateRepository;
import banghak.home.halley.adapter.outbound.persistence.NotificationLogRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyAgentRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyImageRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyOpinionRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyScoreRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyVisitRepository;
import banghak.home.halley.adapter.outbound.persistence.ReferenceTransactionRepository;
import banghak.home.halley.adapter.outbound.persistence.RegulationParamRepository;
import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.adapter.outbound.persistence.UserCriterionScoreRepository;
import banghak.home.halley.domain.geo.LegalDongCode;
import banghak.home.halley.domain.itinerary.PropertyVisit;
import banghak.home.halley.domain.itinerary.TravelMode;
import banghak.home.halley.domain.loan.LoanEstimate;
import banghak.home.halley.domain.loan.ProductType;
import banghak.home.halley.domain.loan.RegulationParam;
import banghak.home.halley.domain.loan.RegulationValueType;
import banghak.home.halley.domain.notification.NotificationEventType;
import banghak.home.halley.domain.notification.NotificationLog;
import banghak.home.halley.domain.notification.NotificationStatus;
import banghak.home.halley.domain.property.Agent;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.FloorBand;
import banghak.home.halley.domain.property.ImageType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.MoveInType;
import banghak.home.halley.domain.property.OpinionType;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.PropertyAgent;
import banghak.home.halley.domain.property.PropertyImage;
import banghak.home.halley.domain.property.PropertyOpinion;
import banghak.home.halley.domain.property.ReferenceDealType;
import banghak.home.halley.domain.property.ReferenceSource;
import banghak.home.halley.domain.property.ReferenceTransaction;
import banghak.home.halley.domain.property.SourceType;
import banghak.home.halley.domain.scoring.CommuteResult;
import banghak.home.halley.domain.scoring.Criterion;
import banghak.home.halley.domain.scoring.CriterionWeight;
import banghak.home.halley.domain.scoring.PropertyScore;
import banghak.home.halley.domain.scoring.ScoreSource;
import banghak.home.halley.domain.scoring.ScoringType;
import banghak.home.halley.domain.scoring.UserCriterionScore;
import banghak.home.halley.domain.setting.ConfigCategory;
import banghak.home.halley.domain.setting.ConfigValueType;
import banghak.home.halley.domain.setting.SystemConfig;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class JooqRepositoryIntegrationTest {

    @Autowired private PropertyRepository propertyRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private PropertyAgentRepository propertyAgentRepository;
    @Autowired private PropertyImageRepository propertyImageRepository;
    @Autowired private CriterionRepository criterionRepository;
    @Autowired private CriterionWeightRepository criterionWeightRepository;
    @Autowired private PropertyScoreRepository propertyScoreRepository;
    @Autowired private UserCriterionScoreRepository userCriterionScoreRepository;
    @Autowired private CommuteResultRepository commuteResultRepository;
    @Autowired private PropertyOpinionRepository propertyOpinionRepository;
    @Autowired private SystemConfigRepository systemConfigRepository;
    @Autowired private NotificationLogRepository notificationLogRepository;
    @Autowired private ReferenceTransactionRepository referenceTransactionRepository;
    @Autowired private LoanEstimateRepository loanEstimateRepository;
    @Autowired private PropertyVisitRepository propertyVisitRepository;
    @Autowired private RegulationParamRepository regulationParamRepository;
    @Autowired private LegalDongCodeRepository legalDongCodeRepository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void propertyRoundTrip() {
        Property saved = propertyRepository.save(new Property(
                null, "독립문삼호", "101동", DealType.SALE, 1_350_000_000L, 300_000,
                "서울 서대문구 통일로", "서울 서대문구 홍제동",
                new BigDecimal("37.57"), new BigDecimal("126.96"),
                new BigDecimal("84.93"), new BigDecimal("59.90"),
                "7", 7, 15, FloorBand.HIGH, "3/2", "남동향", 1995, MoveInType.IMMEDIATE, null,
                new BigDecimal("1.0"), 300, "중앙난방", 5, 1_350_000_000L, null, null, null, null, null, null, null, null, null, null, null,
                SourceType.PASTE, "https://example.com", "12345", "raw", "v1",
                objectMapper.createObjectNode().put("price", "EXACT"),
                false, ListingStatus.ACTIVE, true, null, 0, null, 1L, "테스터", null, null));

        Property found = propertyRepository.findById(saved.id()).orElseThrow();
        assertThat(found.name()).isEqualTo("독립문삼호");
        assertThat(found.dealType()).isEqualTo(DealType.SALE);
        assertThat(found.floorBand()).isEqualTo(FloorBand.HIGH);
        assertThat(found.moveInType()).isEqualTo(MoveInType.IMMEDIATE);
        assertThat(found.sourceType()).isEqualTo(SourceType.PASTE);
        assertThat(found.listingStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(found.parseConfidence().get("price").asString()).isEqualTo("EXACT");
        assertThat(found.createdAt()).isNotNull();
    }

    @Test
    void agentRoundTrip() {
        Agent saved = agentRepository.save(new Agent(
                null, "한빛공인중개", "김중개", "02-123-4567", "010-1234-5678",
                "11111-2222-33333", "서울 서대문구", new BigDecimal("37.5"), new BigDecimal("126.9")));

        Agent found = agentRepository.findById(saved.id()).orElseThrow();
        assertThat(found.officeName()).isEqualTo("한빛공인중개");
        assertThat(found.agentName()).isEqualTo("김중개");
    }

    @Test
    void propertyAgentRoundTrip() {
        PropertyAgent saved = propertyAgentRepository.save(new PropertyAgent(1L, 2L, true));

        PropertyAgent found = propertyAgentRepository.findById(1L, 2L).orElseThrow();
        assertThat(found.isPrimary()).isTrue();
    }

    @Test
    void propertyImageRoundTrip() {
        PropertyImage saved = propertyImageRepository.save(new PropertyImage(null, 1L, ImageType.FLOOR_PLAN, "/img/1.png", 0));

        PropertyImage found = propertyImageRepository.findById(saved.id()).orElseThrow();
        assertThat(found.imageType()).isEqualTo(ImageType.FLOOR_PLAN);
        assertThat(found.storagePath()).isEqualTo("/img/1.png");
    }

    @Test
    void criterionRoundTrip() {
        criterionRepository.save(new Criterion("CUSTOM", "테스트", ScoringType.AUTO, true));

        Criterion found = criterionRepository.findById("CUSTOM").orElseThrow();
        assertThat(found.scoringType()).isEqualTo(ScoringType.AUTO);
        assertThat(found.enabled()).isTrue();
    }

    @Test
    void criterionWeightRoundTrip() {
        criterionWeightRepository.save(new CriterionWeight("CUSTOM", 99, new BigDecimal("3.0"), null));

        CriterionWeight found = criterionWeightRepository.findById("CUSTOM").orElseThrow();
        assertThat(found.priorityRank()).isEqualTo(99);
        assertThat(found.updatedAt()).isNotNull();
    }

    @Test
    void propertyScoreRoundTrip() {
        PropertyScore saved = propertyScoreRepository.save(new PropertyScore(
                null, 90_010L, "PRICE", new BigDecimal("80.0"), null, new BigDecimal("80.0"),
                ScoreSource.AUTO, null, "호가 8억원 / 예산상한 10억원", null));

        PropertyScore found = propertyScoreRepository.findById(saved.id()).orElseThrow();
        assertThat(found.scoreSource()).isEqualTo(ScoreSource.AUTO);
        assertThat(found.effectiveScore()).isEqualByComparingTo("80.0");
    }

    @Test
    void userCriterionScoreRoundTrip() {
        userCriterionScoreRepository.save(new UserCriterionScore(90_011L, 90_011L, "COMFORT", 4));

        UserCriterionScore found = userCriterionScoreRepository.findById(90_011L, 90_011L, "COMFORT").orElseThrow();
        assertThat(found.score()).isEqualTo(4);
    }

    @Test
    void commuteResultRoundTrip() {
        commuteResultRepository.save(new CommuteResult(
                1L, 2L, 45, 1, 10, objectMapper.createObjectNode().put("mode", "TRANSIT"), null));

        CommuteResult found = commuteResultRepository.findById(1L, 2L).orElseThrow();
        assertThat(found.totalMinutes()).isEqualTo(45);
        assertThat(found.pathSummary().get("mode").asString()).isEqualTo("TRANSIT");
    }

    @Test
    void propertyOpinionRoundTrip() {
        PropertyOpinion saved = propertyOpinionRepository.save(new PropertyOpinion(
                null, 1L, 2L, OpinionType.MERIT, "역세권 좋음", 0));

        PropertyOpinion found = propertyOpinionRepository.findById(saved.id()).orElseThrow();
        assertThat(found.opinionType()).isEqualTo(OpinionType.MERIT);
    }

    @Test
    void systemConfigRoundTrip() {
        systemConfigRepository.save(new SystemConfig(
                "custom.config.key", "value", ConfigValueType.STRING,
                ConfigCategory.BATCH, "테스트 설정", false, null, null));

        SystemConfig found = systemConfigRepository.findById("custom.config.key").orElseThrow();
        assertThat(found.category()).isEqualTo(ConfigCategory.BATCH);
        assertThat(found.valueType()).isEqualTo(ConfigValueType.STRING);
    }

    @Test
    void notificationLogRoundTrip() {
        NotificationLog saved = notificationLogRepository.save(new NotificationLog(
                null, NotificationEventType.PROPERTY_CREATED, 1L, "slack",
                NotificationStatus.SENT, 0, null,
                objectMapper.createObjectNode().put("name", "독립문삼호"),
                null, null));

        NotificationLog found = notificationLogRepository.findById(saved.id()).orElseThrow();
        assertThat(found.eventType()).isEqualTo(NotificationEventType.PROPERTY_CREATED);
        assertThat(found.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(found.payload().get("name").asString()).isEqualTo("독립문삼호");
    }

    @Test
    void referenceTransactionRoundTrip() {
        ReferenceTransaction saved = referenceTransactionRepository.save(new ReferenceTransaction(
                null, 1L, ReferenceDealType.TRADE, LocalDate.of(2026, 7, 1),
                1_290_000_000L, new BigDecimal("84.90"), 2, ReferenceSource.MINISTRY_TRADE, null));

        ReferenceTransaction found = referenceTransactionRepository.findById(saved.id()).orElseThrow();
        assertThat(found.dealType()).isEqualTo(ReferenceDealType.TRADE);
        assertThat(found.source()).isEqualTo(ReferenceSource.MINISTRY_TRADE);
    }

    @Test
    void loanEstimateRoundTrip() {
        LoanEstimate saved = loanEstimateRepository.save(new LoanEstimate(
                null, 1L, ProductType.MORTGAGE, new BigDecimal("0.4"),
                540_000_000L, 540_000_000L, 540_000_000L, 810_000_000L, 30_000_000L,
                objectMapper.createObjectNode().put("income", "1억"), null));

        LoanEstimate found = loanEstimateRepository.findById(saved.id()).orElseThrow();
        assertThat(found.productType()).isEqualTo(ProductType.MORTGAGE);
        assertThat(found.assumptions().get("income").asString()).isEqualTo("1억");
    }

    @Test
    void propertyVisitRoundTrip() {
        // (property_id, user_id) 가 유니크다 — 이 테스트만의 값을 쓴다
        final long propertyId = System.nanoTime() % 1_000_000L + 1_000L;

        propertyVisitRepository.mark(propertyId, 7L, Instant.parse("2026-09-06T01:00:00Z"));

        assertThat(propertyVisitRepository.exists(propertyId, 7L)).isTrue();
        assertThat(propertyVisitRepository.findByUser(7L))
                .extracting(PropertyVisit::propertyId)
                .contains(propertyId);
    }

    @Test
    void markIsIdempotentAndKeepsTheFirstTime() {
        // 두 번 눌러도 한 줄이고, 처음 간 시각이 남는다 — 나중 클릭이 덮으면 기록이 아니다
        final long propertyId = System.nanoTime() % 1_000_000L + 2_000L;
        final Instant first = Instant.parse("2026-09-06T01:00:00Z");

        propertyVisitRepository.mark(propertyId, 8L, first);
        propertyVisitRepository.mark(propertyId, 8L, Instant.parse("2026-09-20T05:00:00Z"));

        assertThat(propertyVisitRepository.findByUser(8L))
                .filteredOn(v -> v.propertyId().equals(propertyId))
                .singleElement()
                .extracting(PropertyVisit::visitedAt)
                .isEqualTo(first);
    }

    @Test
    void clearRemovesTheVisit() {
        final long propertyId = System.nanoTime() % 1_000_000L + 3_000L;
        propertyVisitRepository.mark(propertyId, 9L, Instant.now());

        propertyVisitRepository.clear(propertyId, 9L);

        assertThat(propertyVisitRepository.exists(propertyId, 9L)).isFalse();
    }

    @Test
    void regulationParamRoundTrip() {
        RegulationParam saved = regulationParamRepository.save(new RegulationParam(
                null, "2025-10-15", "LTV_RATE", "0.4", RegulationValueType.DECIMAL,
                "LTV 비율", null, null));

        RegulationParam found = regulationParamRepository.findById(saved.id()).orElseThrow();
        assertThat(found.valueType()).isEqualTo(RegulationValueType.DECIMAL);
        assertThat(found.paramValue()).isEqualTo("0.4");
    }

    @Test
    void legalDongCodeRoundTrip() {
        legalDongCodeRepository.save(new LegalDongCode(
                "1111010100", "서울특별시", "종로구", "청운동", null, true, null));

        LegalDongCode found = legalDongCodeRepository.findById("1111010100").orElseThrow();
        assertThat(found.sigungu()).isEqualTo("종로구");
        assertThat(found.dongName()).isEqualTo("청운동");
    }
}
