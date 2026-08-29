package banghak.home.halley.domain.finance;

import java.util.Arrays;
import java.util.Optional;

/**
 * 금감원 오픈API의 권역코드 `topFinGrpNo` (설계 I77).
 *
 * <p>필수 파라미터이고 권역마다 따로 불러야 합니다 — 한 번에 전 권역을 받는 값이 없습니다.
 */
public enum FinanceGroup {

    BANK("020000", "은행"),
    CREDIT_FINANCE("030200", "여신전문"),
    SAVINGS_BANK("030300", "저축은행"),
    INSURANCE("050000", "보험"),
    INVESTMENT("060000", "금융투자");

    private final String code;
    private final String label;

    FinanceGroup(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static Optional<FinanceGroup> fromCode(String code) {
        return Arrays.stream(values()).filter(g -> g.code.equals(code)).findFirst();
    }
}
