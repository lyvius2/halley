package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.SystemConfigResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateConfigRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class SystemConfigServiceTest {

    @Autowired
    private SystemConfigService systemConfigService;

    @Test
    @DisplayName("시스템 설정 목록은 시드된 기본값을 반환한다")
    void listSeededDefaults() {
        // when
        final List<SystemConfigResponse> configs = systemConfigService.list();

        // then
        assertThat(configs).extracting(SystemConfigResponse::configKey)
                .contains("batch.listingCheck.enabled", "batch.listingCheck.cron",
                        "batch.listingCheck.failThreshold", "scoring.weightCurve",
                        "scoring.floorPeak", "loan.regulation.profile");
    }

    @Test
    @DisplayName("설정을 수정하면 값이 갱신된다")
    void updateChangesValue() {
        // given
        final String original = systemConfigService.list().stream()
                .filter(c -> c.configKey().equals("batch.listingCheck.cron"))
                .map(SystemConfigResponse::configValue)
                .findFirst().orElseThrow();

        // when
        final List<SystemConfigResponse> updated = systemConfigService.update(
                List.of(new UpdateConfigRequest("batch.listingCheck.cron", "0 0 10 * * *")));

        // then
        assertThat(updated).filteredOn(c -> c.configKey().equals("batch.listingCheck.cron"))
                .singleElement().satisfies(c -> assertThat(c.configValue()).isEqualTo("0 0 10 * * *"));

        // restore
        systemConfigService.update(List.of(new UpdateConfigRequest("batch.listingCheck.cron", original)));
    }
}
