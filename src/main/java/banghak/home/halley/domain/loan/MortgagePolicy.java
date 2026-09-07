package banghak.home.halley.domain.loan;

import java.math.BigDecimal;
import java.util.Map;

/** LTV 비율을 규정으로 판정한다. */
public final class MortgagePolicy {

    private static final String RATE_PREFIX = "ltv.rate.";
    private static final String FIRST_HOME_RATE = "ltv.rate.firstHome";
    private static final String FIRST_HOME_CAP = "ltv.cap.firstHome";

    private MortgagePolicy() {
    }


    public static LtvDecision decide(RegulationZone zone,
                                     HouseOwnership ownership,
                                     boolean firstHome,
                                     Map<String, String> overrides,
                                     RegulationParams params) {
        if (firstHome) {
            final BigDecimal rate = decimal(overrides, FIRST_HOME_RATE, params.ltvRate());
            final long cap = longValue(overrides, FIRST_HOME_CAP, params.totalCap());
            return new LtvDecision(rate, cap, zone,
                    "생애최초 우대 " + percent(rate) + " (총액 상한 " + eok(cap) + ")");
        }
        final String key = RATE_PREFIX + zone.segment() + "." + ownership.segment();
        final BigDecimal rate = decimal(overrides, key, params.ltvRate());
        if (rate.signum() == 0) {
            return new LtvDecision(rate, params.totalCap(), zone,
                    zone.label() + " " + ownership.label() + " — 주택담보대출이 제한됩니다");
        }
        return new LtvDecision(rate, params.totalCap(), zone,
                zone.label() + " · " + ownership.label() + " → LTV " + percent(rate));
    }

    private static String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    private static String eok(long won) {
        return BigDecimal.valueOf(won)
                .divide(BigDecimal.valueOf(100_000_000L), 1, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "억";
    }

    private static BigDecimal decimal(Map<String, String> values, String key, BigDecimal fallback) {
        try {
            final String raw = values == null ? null : values.get(key);
            return raw == null || raw.isBlank() ? fallback : new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long longValue(Map<String, String> values, String key, long fallback) {
        try {
            final String raw = values == null ? null : values.get(key);
            return raw == null || raw.isBlank() ? fallback : Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
