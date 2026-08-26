package banghak.home.halley.domain.scoring.engine;

import java.math.BigDecimal;

public final class WeightCurve {

    private WeightCurve() {
    }

    /**
     * weight(rank) = 3.0 − (rank − 1) × 0.2 (등차, 12개 항목 — Session I29)
     */
    public static BigDecimal weightFor(int rank) {
        return BigDecimal.valueOf(30L - 2L * (rank - 1L), 1);
    }
}
