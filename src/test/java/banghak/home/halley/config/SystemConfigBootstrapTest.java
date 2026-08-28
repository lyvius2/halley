package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.domain.setting.ConfigCategory;
import banghak.home.halley.domain.setting.ConfigValueType;
import banghak.home.halley.domain.setting.SystemConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class SystemConfigBootstrapTest {

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @Autowired
    private SystemConfigBootstrap systemConfigBootstrap;

    @Test
    @DisplayName("기존 DB에 남아 있는 채점 설정 키는 부팅 시 제거된다 (I41 옵션 B)")
    void removesObsoleteScoringKeysOnStartup() {
        // given
        systemConfigRepository.save(new SystemConfig(
                "scoring.floorPeak", "15", ConfigValueType.INT, ConfigCategory.SCORING,
                "층수 최고점 임계값", false, null, null));

        // when
        systemConfigBootstrap.run(null);

        // then
        assertThat(systemConfigRepository.findById("scoring.floorPeak")).isEmpty();
    }

    @Test
    @DisplayName("배치·대출 설정은 부팅 시 그대로 유지된다")
    void keepsRemainingSeeds() {
        // when
        systemConfigBootstrap.run(null);

        // then
        assertThat(systemConfigRepository.findById("batch.listingCheck.cron")).isPresent();
        assertThat(systemConfigRepository.findById("loan.regulation.profile")).isPresent();
    }
}
