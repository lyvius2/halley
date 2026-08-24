package banghak.home.halley.domain.scoring;

import java.math.BigDecimal;
import java.time.Instant;

public record CriterionWeight(
        String criterionCode,
        Integer priorityRank,
        BigDecimal weight,
        Instant updatedAt
) {
}
