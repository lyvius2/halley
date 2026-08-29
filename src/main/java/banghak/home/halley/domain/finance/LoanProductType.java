package banghak.home.halley.domain.finance;

/**
 * 금감원이 나누는 대출 상품 구분 (설계 I77).
 *
 * <p>엔드포인트가 다르고 옵션 항목도 다릅니다 — 주담대에는 담보유형(`mrtg_type`)이 있고
 * 전세자금대출에는 없습니다.
 */
public enum LoanProductType {

    MORTGAGE("주택담보대출", "mortgageLoanProductsSearch"),
    JEONSE("전세자금대출", "rentHouseLoanProductsSearch");

    private final String label;
    private final String path;

    LoanProductType(String label, String path) {
        this.label = label;
        this.path = path;
    }

    public String label() {
        return label;
    }

    /** 엔드포인트 경로 조각. */
    public String path() {
        return path;
    }
}
