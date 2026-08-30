package banghak.home.halley.domain.user;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 회원.
 *
 * @param groupId 속한 그룹 (설계 I87). <b>admin은 null</b>이며 어느 그룹에도 속하지 않고
 *                모든 그룹의 매물을 봅니다
 */
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
        /**
         * 본인이 프로필을 확인했는지 (설계 I100).
         *
         * <p>관리자가 대신 넣은 값은 <b>채워져 있을 뿐</b>입니다. 직장이 어디인지, 현금이
         * 얼마인지는 본인만 압니다 — 한 번은 보고 넘어가야 합니다.
         */
        boolean profileConfirmed,
        Long availableBudget,
        Long annualIncome,
        Long existingLoan,
        boolean enabled,
        Instant disabledAt,
        Long disabledBy,
        Instant createdAt
) {

    /** 그룹을 옮긴다 (설계 I87). */
    public User withGroupId(Long groupId) {
        return new User(id(), loginId(), nickname(), groupId, passwordHash(), role(), workplaceName(), workplaceLat(), workplaceLng(), mustChangePassword(), false, availableBudget(), annualIncome(), existingLoan(), enabled(), disabledAt(), disabledBy(), createdAt());
    }


    /**
     * 계정 초기 설정이 끝났는지 (설계 6.1 · I48 · I51 · I74).
     * 닉네임은 로그인 ID와 분리된 표시 이름이라 최초 설정에서 받고,
     * 직장 좌표가 없으면 `COMMUTE`가, 보유 현금이 0이면 `PRICE`가 계산되지 않는다.
     * 연소득은 대출 한도(DSR)의 유일한 입력이라 함께 받는다 (설계 I55).
     * 기존 대출액은 대부분 0이므로 필수로 보지 않는다.
     */
    public boolean profileComplete() {
        return nickname != null && !nickname.isBlank()
                && workplaceName != null && !workplaceName.isBlank()
                && workplaceLat != null && workplaceLng != null
                && availableBudget != null && availableBudget > 0
                && annualIncome != null && annualIncome > 0;
    }

    /** DSR 산정에 쓰는 연소득. 미입력이면 0 (설계 I55). */
    public long annualIncomeOrZero() {
        return annualIncome == null ? 0L : annualIncome;
    }

    /** 기존 대출 잔액. 대부분 0이라 미입력을 0으로 본다. */
    public long existingLoanOrZero() {
        return existingLoan == null ? 0L : existingLoan;
    }

    /** 보유 현금 — `availableBudget`은 예산 상한이자 자기자본이다 (설계 I55). */
    public long cashOrZero() {
        return availableBudget == null ? 0L : availableBudget;
    }
}
