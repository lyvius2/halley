package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;

public record UpdateUserRequest(
        String nickname,
        String email,
        String workplaceName,
        BigDecimal workplaceLat,
        BigDecimal workplaceLng,
        Long availableBudget
) {
}
