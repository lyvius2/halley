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
            new Seed("loan.regulation.profile", "2025-10-15", ConfigValueType.STRING, ConfigCategory.LOAN, "규제 파라미터 세트"));
    
    /**
     * 없앤 기능이 남긴 설정 (설계 I187).
     *
     * <p><b>코드를 지워도 DB 행은 남습니다.</b> 생존 확인 배치를 걷어냈는데(I157)
     * 관리자 설정 화면에 "생존 확인 배치 cron"이 계속 떴습니다 — <b>있지도 않은 배치의
     * 설정을 고칠 수 있는 것처럼</b> 보였습니다.
     */
    private static final List<String> OBSOLETE_KEYS = List.of(
            "scoring.weightCurve", "scoring.floorPeak",
            "batch.listingCheck.enabled", "batch.listingCheck.cron",
            "batch.listingCheck.failThreshold", "batch.listingCheck.autoDisable",
            // 판매완료 알림은 생존 확인 배치만 쓰던 것이다 (설계 I171)
            "slack.notify.soldOut");

    private final SystemConfigRepository systemConfigRepository;

    public SystemConfigBootstrap(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        removeObsolete();
        if (!systemConfigRepository.findAll().isEmpty()) {
            return;
        }
        for (final Seed seed : DEFAULTS) {
            systemConfigRepository.save(new SystemConfig(
                    seed.key(), seed.value(), seed.valueType(), seed.category(),
                    seed.description(), false, null, null));
        }
        log.info("Seeded {} system config entries.", DEFAULTS.size());
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
