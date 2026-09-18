package banghak.home.halley.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 기동 시 금융결제원 연동 준비 상태를 민감 정보 없이 기록한다. */
@Slf4j
@Component
public class KftcOpenBankingReadinessReporter implements ApplicationRunner {

    private final KftcOpenBankingProperties properties;

    public KftcOpenBankingReadinessReporter(KftcOpenBankingProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        final KftcOpenBankingReadiness readiness = properties.readiness();
        if (!readiness.enabled()) {
            log.info("KFTC Open Banking integration is disabled");
            return;
        }
        if (readiness.isReady()) {
            log.info("KFTC Open Banking configuration is ready for {}", properties.getEnvironment());
            return;
        }
        log.warn("KFTC Open Banking configuration is incomplete: {}", readiness.missingRequirements());
    }
}
