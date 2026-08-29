package banghak.home.halley.domain.loan;

/**
 * 담보가치를 어디서 얻었는지 (설계 I64-1).
 *
 * <p>출처마다 신뢰도가 다릅니다. 호가로 계산한 값과 KB시세로 계산한 값이 화면에서 같은 얼굴로
 * 보이면 안 되므로 산출 결과에 함께 실어 보냅니다.
 */
public enum CollateralSource {

    /** KB시세 — 은행이 LTV를 매길 때 실제로 쓰는 값. 가장 정확하다. */
    KB_PRICE("KB시세"),

    /** 동일 단지·유사 면적의 최근 실거래 중앙값. 국토부 자료라 시세보다 뒤처질 수 있다. */
    RECENT_TRADE("최근 실거래가"),

    /** 공시가격을 현실화율로 나눈 환산값. 편차가 크다. */
    OFFICIAL_PRICE("공시가격 환산"),

    /** 호가. 파는 쪽이 부른 값이라 담보가치보다 높기 쉽다. */
    ASKING_PRICE("호가");

    private final String label;

    CollateralSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
