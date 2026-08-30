package banghak.home.halley.adapter.inbound.web.dto;

import java.time.Instant;

/** @param memberCount 남은 인원. 1이면 내가 나갈 때 그룹과 매물이 사라진다 (설계 I89 · 규칙 4) */
public record GroupResponse(Long id, String name, int memberCount, Instant createdAt) {
}
