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

    /**
     * 좌표가 없으면 POI·통근 채점을 시도조차 할 수 없다. "데이터 없음"으로 뭉뚱그리면 사용자가
     * 외부 API 장애로 오해하므로, 무엇을 해야 하는지까지 사유에 담는다.
     */
    public static ScoreResult missingCoordinates() {
        return new ScoreResult(null, "매물 좌표 없음 — 매물 수정에서 주소를 검색해 좌표를 채우세요");
    }

    public boolean isComputed() {
        return score != null;
    }
}
