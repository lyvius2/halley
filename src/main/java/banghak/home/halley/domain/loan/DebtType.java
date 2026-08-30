package banghak.home.halley.domain.loan;

/**
 * 기존 부채의 종류 (설계 I92 · 로드맵 5단계).
 *
 * <p><b>DSR 산정만기가 종류마다 다릅니다.</b> 실제 만기가 얼마든 규제가 정한 기간으로
 * 나눠 연간 원리금을 구합니다 — 짧게 잡을수록 연간 상환액이 커지고 한도가 줄어듭니다.
 *
 * <p>이걸 구분하지 않고 전부 주담대(30년)로 보면 <b>한도가 실제보다 크게 나옵니다.</b>
 * 신용대출 1억은 30년으로 보면 연 640만원이지만 5년으로 보면 연 2,200만원입니다.
 *
 * @param dsrYears     DSR 산정만기(년)
 * @param interestOnly 원금을 DSR에 넣지 않는지. 전세자금대출은 이자만 본다
 */
public enum DebtType {

    /** 주택담보대출 — 실제 만기를 쓰되 규제 파라미터의 기본 기간으로 본다. */
    MORTGAGE("주택담보대출", 30, false),
    /** 신용대출 — 실제 만기와 무관하게 5년으로 본다. */
    CREDIT("신용대출", 5, false),
    /**
     * 마이너스통장 — <b>쓴 금액이 아니라 한도 전체</b>를 부채로 본다. 언제든 다 쓸 수
     * 있기 때문이다. 입력도 한도 금액을 받는다.
     */
    NEGATIVE_ACCOUNT("마이너스통장", 5, false),
    /** 전세자금대출 — 원금은 빼고 이자만 센다. */
    JEONSE("전세자금대출", 4, true),
    /** 기타담보대출 — 예적금·주식 담보 등. */
    OTHER_SECURED("기타담보대출", 8, false),
    /** 할부·리스 — 실제 만기가 짧다. */
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
