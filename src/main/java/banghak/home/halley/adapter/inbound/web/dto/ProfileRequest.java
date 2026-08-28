package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;

/** 내 프로필 저장 — 직장 위치와 가용 예산 (설계 8장 · I48). */
public record ProfileRequest(
        String workplaceName,
        BigDecimal workplaceLat,
        BigDecimal workplaceLng,
        Long availableBudget
) {
}
