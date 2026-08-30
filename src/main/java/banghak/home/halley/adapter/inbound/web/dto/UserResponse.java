package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.user.UserRole;

import java.math.BigDecimal;
import java.time.Instant;

public record UserResponse(
        Long id,
        String loginId,
        String nickname,
        /** 소속 그룹 (설계 I103). admin은 null이다 */
        Long groupId,
        String groupName,
        UserRole role,
        String workplaceName,
        BigDecimal workplaceLat,
        BigDecimal workplaceLng,
        Long availableBudget,
        Long annualIncome,
        Long existingLoan,
        boolean enabled,
        boolean mustChangePassword,
        Instant createdAt
) {
}
