package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.SystemConfigResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateConfigRequest;
import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.domain.setting.SystemConfig;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    public SystemConfigService(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    public List<SystemConfigResponse> list() {
        return systemConfigRepository.findAll().stream()
                .sorted(Comparator.comparing(SystemConfig::configKey))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<SystemConfigResponse> update(List<UpdateConfigRequest> requests) {
        for (final UpdateConfigRequest request : requests) {
            systemConfigRepository.findById(request.configKey()).ifPresent(existing ->
                    systemConfigRepository.update(new SystemConfig(
                            existing.configKey(),
                            request.configValue(),
                            existing.valueType(),
                            existing.category(),
                            existing.description(),
                            existing.masked(),
                            currentAdminId(),
                            existing.updatedAt())));
        }
        return list();
    }

    private SystemConfigResponse toResponse(SystemConfig config) {
        return new SystemConfigResponse(
                config.configKey(),
                mask(config),
                config.valueType(),
                config.category(),
                config.description(),
                config.updatedAt());
    }

    private String mask(SystemConfig config) {
        if (!config.masked() || config.configValue() == null) {
            return config.configValue();
        }
        if (config.configValue().length() <= 8) {
            return "****";
        }
        return config.configValue().substring(0, 4) + "****"
                + config.configValue().substring(config.configValue().length() - 4);
    }

    private Long currentAdminId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HalleyUserDetails principal) {
            return principal.getId();
        }
        return null;
    }
}
