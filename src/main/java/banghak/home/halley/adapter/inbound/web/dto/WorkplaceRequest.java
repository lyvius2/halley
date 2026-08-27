package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;

public record WorkplaceRequest(
        String workplaceName,
        BigDecimal workplaceLat,
        BigDecimal workplaceLng
) {
}
