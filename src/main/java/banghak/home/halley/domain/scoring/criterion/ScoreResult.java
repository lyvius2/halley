package banghak.home.halley.domain.scoring.criterion;

import java.math.BigDecimal;

public record ScoreResult(BigDecimal score, String fallbackReason) {

    public static ScoreResult scored(double score) {
        final double roundValue = Math.round(score * 100.0) / 100.0;
        return new ScoreResult(BigDecimal.valueOf(roundValue), null);
    }

    public static ScoreResult missing(String reason) {
        return new ScoreResult(null, reason);
    }

    public boolean isComputed() {
        return score != null;
    }
}
