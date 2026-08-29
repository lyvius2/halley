package banghak.home.halley.domain.loan;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 전세자금대출 조건을 규제 파라미터에서 읽는다 (설계 I67).
 *
 * <p>보증비율·보증기관 한도는 기관(HUG·HF·SGI)과 정책에 따라 다르고 자주 바뀝니다.
 * `MortgagePolicy`와 같이 <b>순수 함수</b>로 두고 값은 프로파일에서 옵니다.
 *
 * <p>값이 없으면 프로파일의 기본 금리·기간으로 떨어지되, 보증비율만은 0으로 두지 않습니다 —
 * 0이면 한도가 0이 되어 "대출 불가"처럼 보이는데, 그것과 "설정이 없음"은 다릅니다.
 */
public final class JeonsePolicy {

    private static final String RATE = "jeonse.guaranteeRate";
    private static final String CAP = "jeonse.guaranteeCap";
    private static final String INTEREST = "jeonse.interestRate";
    private static final String TERM = "jeonse.termYears";

    /** 설정이 비었을 때 쓰는 보증비율. 0으로 두면 화면이 '대출 불가'로 보인다. */
    private static final BigDecimal DEFAULT_GUARANTEE_RATE = new BigDecimal("0.8");
    /** 전세 계약 주기와 같다. */
    private static final int DEFAULT_TERM_YEARS = 2;

    private JeonsePolicy() {
    }

    public static JeonseTerms resolve(Map<String, String> overrides, RegulationParams params) {
        return new JeonseTerms(
                decimal(overrides, RATE, DEFAULT_GUARANTEE_RATE),
                longValue(overrides, CAP, params.totalCap()),
                decimal(overrides, INTEREST, params.interestRate()),
                intValue(overrides, TERM, DEFAULT_TERM_YEARS));
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

    private static int intValue(Map<String, String> values, String key, int fallback) {
        try {
            final String raw = values == null ? null : values.get(key);
            return raw == null || raw.isBlank() ? fallback : Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
