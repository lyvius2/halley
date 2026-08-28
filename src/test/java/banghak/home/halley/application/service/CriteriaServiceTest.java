package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CriterionWeightResponse;
import banghak.home.halley.config.exception.InvalidWeightsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("local")
class CriteriaServiceTest {

    @Autowired
    private CriteriaService criteriaService;

    @Test
    @DisplayName("가중치 목록은 rank 오름차순으로 반환된다")
    void weightsOrderedByRank() {
        // when
        final List<CriterionWeightResponse> weights = criteriaService.weights();

        // then
        assertThat(weights).hasSize(14);
        assertThat(weights).extracting(CriterionWeightResponse::priorityRank).isSorted();
        assertThat(weights.getFirst().criterionCode()).isEqualTo("COMFORT");
        assertThat(weights.getFirst().weight()).isEqualByComparingTo("3.0");
        assertThat(weights.get(11).criterionCode()).isEqualTo("HOUSEHOLDS");
        assertThat(weights.get(11).weight()).isEqualByComparingTo("0.8");
        // 나중에 추가된 항목은 기존 순위 뒤에 붙는다 (설계 I59 · I61)
        assertThat(weights.get(12).criterionCode()).isEqualTo("LLM_RECOMMENDATION");
        assertThat(weights.get(12).weight()).isEqualByComparingTo("0.6");
        assertThat(weights.get(13).criterionCode()).isEqualTo("COMPARATIVE_ADVANTAGE");
        assertThat(weights.get(13).weight()).isEqualByComparingTo("0.4");
    }

    @Test
    @DisplayName("우선순위를 재배치하면 rank·가중치가 순서대로 갱신된다")
    void updateWeightsReordersAndRestores() {
        // given
        final List<String> original = criteriaService.weights().stream()
                .map(CriterionWeightResponse::criterionCode).toList();
        final List<String> newOrder = new ArrayList<>(original);
        Collections.reverse(newOrder);

        // when
        final List<CriterionWeightResponse> updated = criteriaService.updateWeights(newOrder);

        // then
        assertThat(updated).extracting(CriterionWeightResponse::criterionCode)
                .containsExactlyElementsOf(newOrder);
        assertThat(updated.getFirst().weight()).isEqualByComparingTo("3.0");

        // restore
        final List<CriterionWeightResponse> restored = criteriaService.updateWeights(original);
        assertThat(restored).extracting(CriterionWeightResponse::criterionCode)
                .containsExactlyElementsOf(original);
    }

    @Test
    @DisplayName("잘못된 순서(개수 불일치)는 InvalidWeightsException이 발생한다")
    void invalidOrderThrows() {
        // when
        final InvalidWeightsException ex = assertThrows(
                InvalidWeightsException.class,
                () -> criteriaService.updateWeights(List.of("PRICE")));

        // then
        assertThat(ex.getCode()).isEqualTo("INVALID_WEIGHTS");
    }
}
