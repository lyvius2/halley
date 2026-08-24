package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.user.UserRole;

import java.math.BigDecimal;

public record CreateUserRequest(
        String nickname,
        String email,
        String password,
        UserRole role,
        String workplaceName,
        BigDecimal workplaceLat,
        BigDecimal workplaceLng,
        Long availableBudget
) {
}
