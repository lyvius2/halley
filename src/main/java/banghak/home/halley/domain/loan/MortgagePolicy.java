package banghak.home.halley.domain.loan;

import java.math.BigDecimal;
import java.util.Map;

/**
 * LTV 비율을 규정으로 판정한다 (설계 I66).
 *
 * <p><b>Rule Engine은 외부를 부르지 않습니다.</b> 규제 파라미터와 판정 재료를 받아 비율만 냅니다.
 * 순수 함수여야 규정이 바뀌었을 때 테스트로 확인할 수 있습니다(설계 I64 경계 규칙).
 *
 * <p>비율은 `ltv.rate.{지역}.{보유}` 형태의 파라미터에서 옵니다. 값이 없으면 프로파일의
 * 기본 비율(`ltv.rate`)로 떨어집니다 — <b>규정을 모르는 상태에서 0%로 막아 버리면
 * 화면이 고장난 것처럼 보입니다.</b>
 *
 * <p>생애최초는 지역·보유와 무관하게 우대 비율을 적용하되, 별도의 총액 상한이 붙습니다.
 */
public final class MortgagePolicy {

    private static final String RATE_PREFIX = "ltv.rate.";
    private static final String FIRST_HOME_RATE = "ltv.rate.firstHome";
    private static final String FIRST_HOME_CAP = "ltv.cap.firstHome";

    private MortgagePolicy() {
    }

    /**
     * @param zone       매물이 속한 규제지역 구분
     * @param ownership  신청자의 주택 보유 상태
     * @param firstHome  생애최초 여부
     * @param overrides  `regulation_param`의 원본 값들 — 여기 없는 키는 기본값으로 떨어진다
     * @param params     활성 프로파일의 기본 규제 수치
     */
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
