package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.loan.LoanCalculator;
import banghak.home.halley.domain.property.NearbyFacility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ScoringContext(
        long cashBudget,
        List<Integer> comfortScores,
        LocalDate referenceDate,
        LoanCalculator loanCalculator,
        List<NearbyFacility> nearbyFacilities,
        Map<Long, Integer> commuteMinutes,
        BigDecimal llmScore,
        String llmReason,
        BigDecimal comparativeScore,
        String comparativeReason,
        Integer comparativeRank,
        Integer comparativeCount
) {

    public ScoringContext {
        comfortScores = comfortScores == null ? List.of() : comfortScores;
        nearbyFacilities = nearbyFacilities == null ? List.of() : nearbyFacilities;
        commuteMinutes = commuteMinutes == null ? Map.of() : commuteMinutes;
    }
}
