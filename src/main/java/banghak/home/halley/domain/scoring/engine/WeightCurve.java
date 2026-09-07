package banghak.home.halley.domain.scoring.engine;

import java.math.BigDecimal;

public final class WeightCurve {

    private WeightCurve() {
    }

    /** 항목이 15개를 넘어도 가중치가 0이나 음수가 되지 않게 막는 하한. 음수면 항목의 뜻이 뒤집힌다. */
    private static final BigDecimal FLOOR = BigDecimal.valueOf(2L, 1);

    /**
     * weight(rank) = 3.0 − (rank − 1) × 0.2 (등차 —)
     *
     * 항목 수가 늘어도 동일한 등차식으로 가중치를 계산한다.
     * 16에서 0에 닿으므로 그 아래는 FLOOR로 잡는다.
     */
    public static BigDecimal weightFor(int rank) {
        final BigDecimal weight = BigDecimal.valueOf(30L - 2L * (rank - 1L), 1);
        return weight.compareTo(FLOOR) < 0 ? FLOOR : weight;
    }
}
