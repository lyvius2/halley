package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;

/** 내 프로필 저장 — 닉네임·직장 위치·가용 예산 (설계 7.1 M5 · 8장 · I48). */
public record ProfileRequest(
        String nickname,
        String workplaceName,
        BigDecimal workplaceLat,
        BigDecimal workplaceLng,
        Long availableBudget
) {
}
