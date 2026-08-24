package banghak.home.halley.domain.setting;

import java.time.Instant;

public record SystemConfig(
        String configKey,
        String configValue,
        ConfigValueType valueType,
        ConfigCategory category,
        String description,
        boolean masked,
        Long updatedBy,
        Instant updatedAt
) {
}
