package banghak.home.halley.domain.property;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 국토부 실거래 한 건.
 *
 * @param legalDong 법정동 ({@code umdNm}). 국토부가 <b>이미 주고 있는데</b> 버리고
 *                  있었습니다 (설계 I257) — 이름이 통째로 바뀐 단지를 이것으로 잡습니다
 * @param jibun     번지 ({@code jibun}). {@code 138} 또는 {@code 138-2}
 */
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

    /** 이 거래가 어느 자리인가 (설계 I257). 못 가리면 비어 있다 */
    public java.util.Optional<JibunAddress> lot() {
        return JibunAddress.of(legalDong, jibun);
    }
}
