package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.user.UserRole;

import java.math.BigDecimal;

public record CreateUserRequest(
        String loginId,
        String nickname,
        /** 넣을 그룹 (설계 I87 · 규칙 12). 비우면 새 그룹을 만들어 배정한다 */
        Long groupId,
        String password,
        UserRole role,
        String workplaceName,
        BigDecimal workplaceLat,
        BigDecimal workplaceLng,
        Long availableBudget,
        Long annualIncome,
        Long existingLoan
) {
}
