package banghak.home.halley.domain.loan;

import java.math.BigDecimal;

/**
 * 금리유형과 스트레스 가중치 (설계 I97).
 *
 * <p>스트레스 DSR은 <b>금리가 오를 위험만큼</b> 한도를 줄이는 규제입니다. 그 위험은 금리유형마다
 * 다릅니다 — 만기까지 고정이면 오를 일이 없고, 변동이면 그대로 노출됩니다.
 *
 * <p>지금까지는 유형과 무관하게 가산금리 하나를 더했습니다. <b>고정금리를 골라도 스트레스가
 * 붙어</b> 한도가 실제보다 낮게 나왔습니다.
 *
 * @param weight 스트레스 금리에 곱하는 비율. 순수 고정은 0 — 아예 붙지 않는다
 */
public enum RateType {

    VARIABLE("변동금리", new BigDecimal("1.0")),
    /** 일정 기간 고정 후 변동. 고정기간이 길수록 노출이 적어 규제도 낮게 본다. */
    MIXED("혼합형(고정 후 변동)", new BigDecimal("0.6")),
    /** 일정 주기로만 금리가 바뀐다. */
    PERIODIC("주기형", new BigDecimal("0.3")),
    /** 만기까지 고정 — 오를 위험이 없으므로 스트레스를 붙이지 않는다. */
    FIXED("고정금리", BigDecimal.ZERO);

    private final String label;
    private final BigDecimal weight;

    RateType(String label, BigDecimal weight) {
        this.label = label;
        this.weight = weight;
    }

    public String label() {
        return label;
    }

    public BigDecimal weight() {
        return weight;
    }
}
