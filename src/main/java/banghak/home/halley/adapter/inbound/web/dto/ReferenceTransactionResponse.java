package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReferenceTransactionResponse(
        LocalDate contractDate,
        Long price,
        Integer floorNo,
        BigDecimal areaM2
) {
}
