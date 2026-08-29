package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;

/** 내 프로필 저장 — 닉네임·직장 위치·보유 현금·연소득·기존 대출액 (설계 7.1 M5 · 8장 · I48 · I55). */
public record ProfileRequest(
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
