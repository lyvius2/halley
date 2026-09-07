package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 평형인지" 알 수 없습니다. 실제로 매물의 전용면적에 공급면적이 */
public record ReferenceTransactionResponse(
        LocalDate contractDate,
        Long price,
        Integer floorNo,
        BigDecimal areaM2
) {
}
