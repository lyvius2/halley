package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;

public record UpdateUserRequest(
        String loginId,
        String nickname,
        /** 옮길 그룹 (설계 I103). null이면 지금 그룹을 그대로 둔다 */
        Long groupId,
        String workplaceName,
        BigDecimal workplaceLat,
        BigDecimal workplaceLng,
        Long availableBudget,
        Long annualIncome,
        Long existingLoan
) {
}
