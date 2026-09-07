package banghak.home.halley.domain.property;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 국토부 실거래 한 건. */
public record ReferenceTrade(
        String apartmentName,
        Long dealAmount,
        BigDecimal areaM2,
        Integer floorNo,
        LocalDate contractDate,
        String legalDong,
        String jibun
) {

 /** 동·번지를 안 쓰는 자리에서 (테스트·옛 호출부). */
    public ReferenceTrade(String apartmentName, Long dealAmount, BigDecimal areaM2,
                          Integer floorNo, LocalDate contractDate) {
        this(apartmentName, dealAmount, areaM2, floorNo, contractDate, null, null);
    }

 /** 이 거래가 어느 자리인가. 못 가리면 비어 있다 */
    public java.util.Optional<JibunAddress> lot() {
        return JibunAddress.of(legalDong, jibun);
    }
}
