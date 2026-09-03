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

    /**
     * AI를 쓰는 자리마다 모델을 따로 고른다 (설계 I267).
     * 값은 비워 둔다 — 기본값을 박아 두면 나중에 배포로 기본을 바꿔도 DB 값이 이긴다.
     */
    private static final List<Seed> LLM_SEEDS = Arrays.stream(LlmFeature.values())
            .map(f -> new Seed(f.configKey(), "", ConfigValueType.STRING, ConfigCategory.LLM,
                    f.label() + " — " + f.description()))
            .toList();
    
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

    /**
     * 키 하나씩 본다 (설계 I267) — 표가 비었을 때만 심으면 이미 돌고 있는 곳엔
     * 새 설정이 영영 안 들어간다. 이미 있는 값은 안 건드린다.
     */
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
