package banghak.home.halley.domain.loan;

import java.math.BigDecimal;

/** 금리유형과 스트레스 가중치. */
public enum RateType {

    VARIABLE("변동금리", new BigDecimal("1.0")),
 /** 일정 기간 고정 후 변동. 고정기간이 길수록 노출이 적어 규제도 낮게 본다. */
    MIXED("혼합형(고정 후 변동)", new BigDecimal("0.6")),
 /** 일정 주기로만 금리가 바뀐다. */
    PERIODIC("주기형", new BigDecimal("0.3")),
 /** 만기까지 고정. 오를 위험이 없으므로 스트레스를 붙이지 않는다. */
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
