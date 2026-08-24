package banghak.home.halley.domain.property;

public record PropertyAgent(
        Long propertyId,
        Long agentId,
        boolean isPrimary
) {
}
