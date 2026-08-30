package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.LegalDongCodeRepository;
import banghak.home.halley.domain.geo.LegalDongCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * 규제지역 매칭에 쓸 시군구 사전을 채운다 (설계 I78).
 *
 * <p>{@code legal_dong_code}는 원래 카카오로 채우는 <b>지연 캐시</b>라 기동 시 비어 있습니다.
 * 규제지역 적재는 기동 직후에 도는데 그때 사전이 없으면 매칭이 통째로 실패합니다.
 *
 * <p><b>이미 있는 코드는 건드리지 않습니다.</b> 카카오가 채워 둔 동 단위 항목이 더 정확하고,
 * 여기 실린 값은 시군구까지만입니다.
 */
@Slf4j
@Component
// 규제지역 적재(RegulatedAreaBootstrap)가 이 사전을 읽으므로 반드시 먼저 돈다
@Order(10)
public class SigunguCodeBootstrap implements ApplicationRunner {

    private static final String RESOURCE = "data/sigungu-code.csv";
    /** 법정동코드는 10자리. 시군구까지만 알므로 뒤를 0으로 채운다. */
    private static final String DONG_PADDING = "00000";

    private final LegalDongCodeRepository legalDongCodeRepository;

    public SigunguCodeBootstrap(LegalDongCodeRepository legalDongCodeRepository) {
        this.legalDongCodeRepository = legalDongCodeRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        int added = 0;
        int skipped = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                final String[] parts = trimmed.split(",", 3);
                if (parts.length < 3) {
                    log.warn("Malformed sigungu code row - skipping. line={}", trimmed);
                    continue;
                }
                final String code = parts[0].trim() + DONG_PADDING;
                if (legalDongCodeRepository.findById(code).isPresent()) {
                    skipped++;
                    continue;
                }
                legalDongCodeRepository.save(new LegalDongCode(
                        code, parts[1].trim(), parts[2].trim(), null, null, true, Instant.now()));
                added++;
            }
        } catch (Exception e) {
            // 여기서 실패하면 규제지역 적재가 통째로 실패한다. 원인이 보여야 한다
            log.error("Failed to seed sigungu codes - regulated area matching will fail. cause={}",
                    e.toString(), e);
            return;
        }
        log.info("Sigungu codes seeded. added={}, alreadyPresent={}", added, skipped);
    }
}
