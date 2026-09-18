package banghak.home.halley.domain.scoring.criterion;

import java.math.BigDecimal;

public record ScoreResult(BigDecimal score, String fallbackReason, String explanation) {

    public static ScoreResult scored(double score, String explanation) {
        final double roundValue = Math.round(score * 100.0) / 100.0;
        return new ScoreResult(BigDecimal.valueOf(roundValue), null, explanation);
    }

    public static ScoreResult missing(String reason) {
        return new ScoreResult(null, reason, null);
    }

    public static ScoreResult missingCoordinates() {
        return new ScoreResult(null, "매물 좌표 없음 — 매물 수정에서 주소를 검색해 좌표를 채우세요", null);
    }

    public boolean isComputed() {
        return score != null;
    }
}
