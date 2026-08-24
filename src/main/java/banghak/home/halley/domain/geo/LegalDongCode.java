package banghak.home.halley.domain.geo;

import java.time.Instant;

public record LegalDongCode(
        String code,
        String sido,
        String sigungu,
        String dongName,
        String riName,
        boolean isActive,
        Instant updatedAt
) {
}
