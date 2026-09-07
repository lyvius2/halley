package banghak.home.halley.domain.loan;

/** 기존 부채의 종류. */
public enum DebtType {

 /** 주택담보대출. 실제 만기를 쓰되 규제 파라미터의 기본 기간으로 본다. */
    MORTGAGE("주택담보대출", 30, false),
 /** 신용대출. 실제 만기와 무관하게 5년으로 본다. */
    CREDIT("신용대출", 5, false),
 /** 마이너스통장. 쓴 금액이 아니라 한도 전체를 부채로 본다. 언제든 다 쓸 수 */
    NEGATIVE_ACCOUNT("마이너스통장", 5, false),
 /** 전세자금대출. 원금은 빼고 이자만 센다. */
    JEONSE("전세자금대출", 4, true),
 /** 기타담보대출. 예적금·주식 담보 등. */
    OTHER_SECURED("기타담보대출", 8, false),
 /** 할부·리스. 실제 만기가 짧다. */
    INSTALLMENT("할부·리스", 3, false);

    private final String label;
    private final int dsrYears;
    private final boolean interestOnly;

    DebtType(String label, int dsrYears, boolean interestOnly) {
        this.label = label;
        this.dsrYears = dsrYears;
        this.interestOnly = interestOnly;
    }

    public String label() {
        return label;
    }

    public int dsrYears() {
        return dsrYears;
    }

    public boolean interestOnly() {
        return interestOnly;
    }
}
