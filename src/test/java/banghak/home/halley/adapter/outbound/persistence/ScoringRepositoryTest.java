package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.scoring.PropertyScore;
import banghak.home.halley.domain.scoring.ScoreSource;
import banghak.home.halley.domain.scoring.UserCriterionScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class ScoringRepositoryTest {

    @Autowired
    private PropertyScoreRepository propertyScoreRepository;

    @Autowired
    private UserCriterionScoreRepository userCriterionScoreRepository;

    @Test
    @DisplayName("수동 점수 upsert는 기존 행의 manual_score를 갱신한다")
    void upsertManualScoreUpdatesExistingRow() {
        // given
        propertyScoreRepository.upsertManualScore(90_001L, "PRICE", new BigDecimal("60"));

        // when
        propertyScoreRepository.upsertManualScore(90_001L, "PRICE", new BigDecimal("70"));

        // then
        assertThat(propertyScoreRepository.findByPropertyId(90_001L))
                .filteredOn(s -> s.criterionCode().equals("PRICE"))
                .singleElement()
                .satisfies(s -> assertThat(s.manualScore()).isEqualByComparingTo("70"));
    }

    @Test
    @DisplayName("deleteByPropertyId는 해당 매물의 채점 행을 모두 삭제한다")
    void deleteByPropertyId() {
        // given
        propertyScoreRepository.save(new PropertyScore(
                null, 90_002L, "FLOOR", new BigDecimal("80"), null, new BigDecimal("80"),
                ScoreSource.AUTO, null, null));

        // when
        propertyScoreRepository.deleteByPropertyId(90_002L);

        // then
        assertThat(propertyScoreRepository.findByPropertyId(90_002L)).isEmpty();
    }

    @Test
    @DisplayName("사용자 기준 점수 upsert는 기존 행의 score를 갱신한다")
    void userCriterionScoreUpsertUpdatesScore() {
        // given
        userCriterionScoreRepository.upsert(new UserCriterionScore(90_003L, 90_003L, "COMFORT", 4));

        // when
        userCriterionScoreRepository.upsert(new UserCriterionScore(90_003L, 90_003L, "COMFORT", 5));

        // then
        assertThat(userCriterionScoreRepository.findById(90_003L, 90_003L, "COMFORT").orElseThrow().score())
                .isEqualTo(5);
    }
}
