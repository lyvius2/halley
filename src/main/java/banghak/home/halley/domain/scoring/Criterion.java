package banghak.home.halley.domain.scoring;

public record Criterion(
        String code,
        String name,
        ScoringType scoringType,
        boolean enabled
) {
}
