package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.LegalDongCodeRepository;
import banghak.home.halley.application.port.out.external.AdmCodePort;
import banghak.home.halley.domain.geo.AdmArea;
import banghak.home.halley.domain.geo.LegalDongCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** 규제지역 매칭에 쓸 시군구 사전을 V-World에서 받아 채운다. */
@Slf4j
@Component
@Order(10)
public class SigunguCodeBootstrap implements ApplicationRunner {

 /** 법정동코드는 10자리. 시군구까지만 알므로 뒤를 0으로 채운다. */
    private static final String DONG_PADDING = "00000";
    private static final int SIGUNGU_CODE_LENGTH = 5;

    private final AdmCodePort admCodePort;
    private final LegalDongCodeRepository legalDongCodeRepository;

    public SigunguCodeBootstrap(AdmCodePort admCodePort,
                                LegalDongCodeRepository legalDongCodeRepository) {
        this.admCodePort = admCodePort;
        this.legalDongCodeRepository = legalDongCodeRepository;
    }

 /** 여기서 던지면 애플리케이션이 뜨지 않습니다. ApplicationRunner의 예외는 */
    @Override
    public void run(ApplicationArguments args) {
        try {
            build();
        } catch (RuntimeException e) {
            log.error("Sigungu dictionary build failed - regulated area matching will not work. "
                    + "cause={}", e.toString(), e);
        }
    }

    private void build() {
        final int existing = legalDongCodeRepository.countSigungu();
        if (existing > 0) {
            log.info("Sigungu dictionary already present - skipping lookup. entries={}", existing);
            return;
        }
        if (!admCodePort.isEnabled()) {
            log.warn("Cannot build sigungu dictionary - VWorld key not configured. "
                    + "Regulated area seeding will fail.");
            return;
        }
        final List<AdmArea> sidoList = admCodePort.fetchSido();
        if (sidoList.isEmpty()) {
            log.error("Sigungu dictionary is empty - regulated area matching will fail.");
            return;
        }
        int added = 0;
        for (final AdmArea sido : sidoList) {
            for (final AdmArea sigungu : admCodePort.fetchSigungu(sido.code())) {
                if (sigungu.code() == null || sigungu.code().length() != SIGUNGU_CODE_LENGTH) {
                    continue;
                }
                legalDongCodeRepository.save(new LegalDongCode(
                        sigungu.code() + DONG_PADDING,
                        sido.fullName(),
                        sigungu.name(),
                        null, null, true, Instant.now()));
                added++;
            }
        }
        log.info("Sigungu dictionary built from VWorld. sido={}, sigungu={}", sidoList.size(), added);
    }
}
