package banghak.home.halley.domain.scoring;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record CommuteResult(
        Long propertyId,
        Long userId,
        Integer totalMinutes,
        Integer transferCount,
        Integer walkMinutes,
        JsonNode pathSummary,
        Instant fetchedAt
) {
}
