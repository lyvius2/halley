package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param areaM2 전용면적을 함께 보여 줍니다. 없으면 "우리 집과 같은
 *               평형인지" 알 수 없습니다 — 실제로 매물의 전용면적에 공급면적이
 *               들어가 있는 것을 아무도 못 알아챘습니다
 */
public record ReferenceTransactionResponse(
        LocalDate contractDate,
        Long price,
        Integer floorNo,
        BigDecimal areaM2
) {
}
