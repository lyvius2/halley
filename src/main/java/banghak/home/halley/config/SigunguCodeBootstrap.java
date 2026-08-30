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

/**
 * 규제지역 매칭에 쓸 시군구 사전을 V-World에서 받아 채운다 (설계 I78).
 *
 * <p>{@code legal_dong_code}는 원래 카카오로 채우는 <b>지연 캐시</b>라 기동 시 비어 있습니다.
 * 규제지역 적재는 기동 직후에 도는데 그때 사전이 없으면 매칭이 통째로 실패합니다.
 *
 * <p><b>목록을 코드에 박지 않습니다.</b> 행정구역은 실제로 바뀝니다 — 화성시 동탄구가 신설됐고
 * 광주광역시와 전라남도가 통합됐습니다. 박아 둔 목록은 낡아도 낡은 줄 모르고, 그 상태로
 * 규제지역이 엉뚱한 코드에 붙습니다.
 *
 * <p>시도 1회 + 시도별 1회로 스무 번쯤 부르지만 <b>한 번 채우면 다시 부르지 않습니다.</b>
 */
@Slf4j
@Component
// 규제지역 적재(RegulatedAreaBootstrap)가 이 사전을 읽으므로 반드시 먼저 돈다
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

    /**
     * <b>여기서 던지면 애플리케이션이 뜨지 않습니다.</b> `ApplicationRunner`의 예외는
     * 기동 실패로 이어집니다 — 외부 API 한 번 실패했다고 앱 전체가 못 뜨면 안 됩니다
     * (설계 12.2 · I115). 사전이 비면 규제지역 매칭만 못 하고 나머지는 그대로 돕니다.
     *
     * <p>비동기로 비키지 않는 이유: 규제지역 적재(`RegulatedAreaBootstrap`, @Order(20))가
     * 이 사전을 읽습니다. 여기서 먼저 끝내야 순서가 지켜집니다.
     */
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
            // 여기서 조용히 넘어가면 규제지역이 왜 안 들어왔는지 알 수 없다
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
