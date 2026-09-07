package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.domain.llm.LlmFeature;
import banghak.home.halley.domain.setting.ConfigCategory;
import banghak.home.halley.domain.setting.ConfigValueType;
import banghak.home.halley.domain.setting.SystemConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
public class SystemConfigBootstrap implements ApplicationRunner {

    private static final List<Seed> DEFAULTS = List.of(
            new Seed("loan.regulation.profile", "2025-10-15", ConfigValueType.STRING, ConfigCategory.LOAN, "규제 파라미터 세트"));

 /** AI를 쓰는 자리마다 모델을 따로 고른다. */
    private static final List<Seed> LLM_SEEDS = Arrays.stream(LlmFeature.values())
            .map(f -> new Seed(f.configKey(), "", ConfigValueType.STRING, ConfigCategory.LLM,
                    f.label() + " — " + f.description()))
            .toList();

 /** 없앤 기능이 남긴 설정. */
    private static final List<String> OBSOLETE_KEYS = List.of(
            "scoring.weightCurve", "scoring.floorPeak",
            "batch.listingCheck.enabled", "batch.listingCheck.cron",
            "batch.listingCheck.failThreshold", "batch.listingCheck.autoDisable",
            "slack.notify.soldOut");

    private final SystemConfigRepository systemConfigRepository;

    public SystemConfigBootstrap(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

 /** 키 하나씩 본다. 표가 비었을 때만 심으면 이미 돌고 있는 곳엔 */
    @Override
    public void run(ApplicationArguments args) {
        removeObsolete();
        int seeded = 0;
        for (final Seed seed : Stream.concat(DEFAULTS.stream(), LLM_SEEDS.stream()).toList()) {
            if (systemConfigRepository.findById(seed.key()).isPresent()) {
                continue;
            }
            systemConfigRepository.save(new SystemConfig(
                    seed.key(), seed.value(), seed.valueType(), seed.category(),
                    seed.description(), false, null, null));
            seeded++;
        }
        if (seeded > 0) {
            log.info("Seeded {} system config entries.", seeded);
        }
    }

    private void removeObsolete() {
        for (final String key : OBSOLETE_KEYS) {
            if (systemConfigRepository.findById(key).isPresent()) {
                systemConfigRepository.delete(key);
                log.info("Removed obsolete system config: {}", key);
            }
        }
    }

    private record Seed(String key, String value, ConfigValueType valueType,
                        ConfigCategory category, String description) {
    }
}
