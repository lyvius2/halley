package banghak.home.halley.domain.user;

import java.math.BigDecimal;
import java.time.Instant;

/** 회원. */
public record User(
        Long id,
        String loginId,
        String nickname,
        Long groupId,
        String passwordHash,
        UserRole role,
        String workplaceName,
        BigDecimal workplaceLat,
        BigDecimal workplaceLng,
        boolean mustChangePassword,
 /** 본인이 프로필을 확인했는지. */
        boolean profileConfirmed,
        Long availableBudget,
        Long annualIncome,
        Long existingLoan,
        boolean enabled,
        Instant disabledAt,
        Long disabledBy,
        Instant createdAt
) {

 /** 그룹을 옮긴다. */
    public User withGroupId(Long groupId) {
        return new User(id(), loginId(), nickname(), groupId, passwordHash(), role(), workplaceName(), workplaceLat(), workplaceLng(), mustChangePassword(), false, availableBudget(), annualIncome(), existingLoan(), enabled(), disabledAt(), disabledBy(), createdAt());
    }


 /** 계정 초기 설정이 끝났는지. */
    public boolean profileComplete() {
        return nickname != null && !nickname.isBlank()
                && workplaceName != null && !workplaceName.isBlank()
                && workplaceLat != null && workplaceLng != null
                && availableBudget != null && availableBudget > 0
                && annualIncome != null && annualIncome > 0;
    }

 /** DSR 산정에 쓰는 연소득. 미입력이면 0. */
    public long annualIncomeOrZero() {
        return annualIncome == null ? 0L : annualIncome;
    }

 /** 기존 대출 잔액. 대부분 0이라 미입력을 0으로 본다. */
    public long existingLoanOrZero() {
        return existingLoan == null ? 0L : existingLoan;
    }

 /** 보유 현금. availableBudget은 예산 상한이자 자기자본이다. */
    public long cashOrZero() {
        return availableBudget == null ? 0L : availableBudget;
    }
}
