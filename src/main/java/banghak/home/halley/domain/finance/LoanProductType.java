package banghak.home.halley.domain.finance;

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

    /** 엔드포인트 경로 조각.  */
    public String path() {
        return path;
    }
}
