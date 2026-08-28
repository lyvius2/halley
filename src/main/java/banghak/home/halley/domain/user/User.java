package banghak.home.halley.domain.user;

import java.math.BigDecimal;
import java.time.Instant;

public record User(
        Long id,
        String loginId,
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
     * 계정 초기 설정이 끝났는지 (설계 6.1 · I48 · I51).
     * 이메일은 로그인 ID와 분리된 연락처 항목이라 최초 설정에서 받고,
     * 직장 좌표가 없으면 `COMMUTE`가, 가용 예산이 0이면 `PRICE`가 계산되지 않는다.
     */
    public boolean profileComplete() {
        return email != null && !email.isBlank()
                && workplaceName != null && !workplaceName.isBlank()
                && workplaceLat != null && workplaceLng != null
                && availableBudget != null && availableBudget > 0;
    }
}
