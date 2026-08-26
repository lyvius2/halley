package banghak.home.halley.domain.scoring;

import banghak.home.halley.adapter.inbound.web.dto.CriterionWeightResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record CriterionWeight(
        String criterionCode,
        Integer priorityRank,
        BigDecimal weight,
        Instant updatedAt
) {
    public CriterionWeightResponse createCriterionWeightResponse(Map<String, Criterion> criteria) {
        final Criterion criterion = criteria.get(this.criterionCode);
        return new CriterionWeightResponse(
                this.criterionCode,
                criterion == null ? this.criterionCode : criterion.name(),
                criterion == null ? null : criterion.scoringType(),
                this.priorityRank,
                this.weight);
    }
}
