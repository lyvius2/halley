package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.CriterionRepository;
import banghak.home.halley.adapter.outbound.persistence.CriterionWeightRepository;
import banghak.home.halley.domain.scoring.Criterion;
import banghak.home.halley.domain.scoring.CriterionWeight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class CriteriaBootstrapTest {

    @Autowired
    private CriterionRepository criterionRepository;

    @Autowired
    private CriterionWeightRepository criterionWeightRepository;

    @Test
    @DisplayName("12개 채점 기준과 등차 가중치가 시드된다")
    void seedsTwelveCriteriaAndWeights() {
        // when
        final List<Criterion> criteria = criterionRepository.findAll();
        final List<CriterionWeight> weights = criterionWeightRepository.findAll();

        // then
        assertThat(criteria).extracting(Criterion::code)
                .contains("COMFORT", "PRICE", "MOVE_IN", "COMMUTE", "AGE", "FLOOR",
                        "STATION", "EDUCATION", "AMENITY", "PARKING", "GREEN", "HOUSEHOLDS");
        assertThat(weights).extracting(CriterionWeight::criterionCode)
                .contains("COMFORT", "PRICE", "MOVE_IN", "COMMUTE", "AGE", "FLOOR",
                        "STATION", "EDUCATION", "AMENITY", "PARKING", "GREEN", "HOUSEHOLDS");
    }

    @Test
    @DisplayName("HOUSEHOLDS는 rank 12, 가중치 0.8로 시드된다")
    void householdsHasLowestPriority() {
        // when
        final CriterionWeight households = criterionWeightRepository.findById("HOUSEHOLDS").orElseThrow();

        // then
        assertThat(households.priorityRank()).isEqualTo(12);
        assertThat(households.weight()).isEqualByComparingTo("0.8");
    }
}
