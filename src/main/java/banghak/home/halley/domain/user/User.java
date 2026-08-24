package banghak.home.halley.domain.user;

import java.math.BigDecimal;
import java.time.Instant;

public record User(
        Long id,
        String nickname,
        String email,
        String passwordHash,
        UserRole role,
        String workplaceName,
        BigDecimal workplaceLat,
        BigDecimal workplaceLng,
        boolean mustChangePassword,
        Long availableBudget,
        boolean enabled,
        Instant disabledAt,
        Long disabledBy,
        Instant createdAt
) {
}
