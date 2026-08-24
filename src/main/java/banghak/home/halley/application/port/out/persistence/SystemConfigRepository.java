package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.setting.SystemConfig;

import java.util.List;
import java.util.Optional;

public interface SystemConfigRepository {

    SystemConfig save(SystemConfig config);

    Optional<SystemConfig> findById(String configKey);

    List<SystemConfig> findAll();

    void delete(String configKey);
}
