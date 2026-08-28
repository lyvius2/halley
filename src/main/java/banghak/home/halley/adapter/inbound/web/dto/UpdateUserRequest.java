package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;

public record UpdateUserRequest(
        String loginId,
        String nickname,
        String email,
        String workplaceName,
        BigDecimal workplaceLat,
        BigDecimal workplaceLng,
        Long availableBudget,
        Long annualIncome,
        Long existingLoan
) {
}
