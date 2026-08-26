package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.setting.ConfigCategory;
import banghak.home.halley.domain.setting.ConfigValueType;

import java.time.Instant;

public record SystemConfigResponse(
        String configKey,
        String configValue,
        ConfigValueType valueType,
        ConfigCategory category,
        String description,
        Instant updatedAt
) {
}
