package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.domain.setting.ConfigCategory;
import banghak.home.halley.domain.setting.ConfigValueType;
import banghak.home.halley.domain.setting.SystemConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class SystemConfigBootstrap implements ApplicationRunner {

    private static final List<Seed> DEFAULTS = List.of(
            new Seed("batch.listingCheck.enabled", "false", ConfigValueType.BOOL, ConfigCategory.BATCH, "생존 확인 배치 활성화 여부"),
            new Seed("batch.listingCheck.cron", "0 0 9 * * *", ConfigValueType.STRING, ConfigCategory.BATCH, "생존 확인 배치 cron"),
            new Seed("batch.listingCheck.failThreshold", "3", ConfigValueType.INT, ConfigCategory.BATCH, "판매완료 확정 연속 횟수"),
            new Seed("batch.listingCheck.autoDisable", "false", ConfigValueType.BOOL, ConfigCategory.BATCH, "판매완료 확정 시 매물 비활성"),
            new Seed("scoring.weightCurve", "ARITHMETIC", ConfigValueType.STRING, ConfigCategory.SCORING, "우선순위→가중치 변환 방식"),
            new Seed("scoring.floorPeak", "15", ConfigValueType.INT, ConfigCategory.SCORING, "층수 최고점 임계값"),
            new Seed("loan.regulation.profile", "2025-10-15", ConfigValueType.STRING, ConfigCategory.LOAN, "규제 파라미터 세트"));

    private final SystemConfigRepository systemConfigRepository;

    public SystemConfigBootstrap(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!systemConfigRepository.findAll().isEmpty()) {
            return;
        }
        for (final Seed seed : DEFAULTS) {
            systemConfigRepository.save(new SystemConfig(
                    seed.key(), seed.value(), seed.valueType(), seed.category(),
                    seed.description(), false, null, null));
        }
        log.info("★ 시스템 설정 {}건 시드 완료 ★", DEFAULTS.size());
    }

    private record Seed(String key, String value, ConfigValueType valueType,
                        ConfigCategory category, String description) {
    }
}
