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

    /**
     * 채점에 필요한 프로필이 채워졌는지 (설계 6.1 · I48).
     * 직장 좌표가 없으면 `COMMUTE`가, 가용 예산이 0이면 `PRICE`가 계산되지 않는다.
     */
    public boolean profileComplete() {
        return workplaceName != null && !workplaceName.isBlank()
                && workplaceLat != null && workplaceLng != null
                && availableBudget != null && availableBudget > 0;
    }
}
