package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.LegalDongCodeRepository;
import banghak.home.halley.domain.geo.LegalDongCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 법정동코드 대표 시드 (설계 5.5). 전체 ~4만 건은 행정안전부 공개 데이터로 적재해야 하며,
 * 여기서는 자주 쓰는 법정동 일부만 시드한다. 코드 앞 5자리는 시군구코드(LAWD_CD)로 사용된다.
 */
@Slf4j
@Component
public class LegalDongCodeBootstrap implements ApplicationRunner {

    private static final List<LegalDongCode> SEED = List.of(
            new LegalDongCode("1111000000", "서울특별시", "종로구", "누상동", null, true, Instant.now()),
            new LegalDongCode("1111000100", "서울특별시", "종로구", "청운동", null, true, Instant.now()),
            new LegalDongCode("1168000000", "서울특별시", "마포구", "서교동", null, true, Instant.now()),
            new LegalDongCode("1168000100", "서울특별시", "마포구", "성산동", null, true, Instant.now()),
            new LegalDongCode("1156000000", "서울특별시", "영등포구", "여의동", null, true, Instant.now()),
            new LegalDongCode("1141000000", "서울특별시", "강서구", "마곡동", null, true, Instant.now()),
            new LegalDongCode("1123000000", "서울특별시", "성북구", "정릉동", null, true, Instant.now()),
            new LegalDongCode("1168000200", "서울특별시", "마포구", "상수동", null, true, Instant.now()));

    private final LegalDongCodeRepository legalDongCodeRepository;

    public LegalDongCodeBootstrap(LegalDongCodeRepository legalDongCodeRepository) {
        this.legalDongCodeRepository = legalDongCodeRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (legalDongCodeRepository.findById("1111000000").isPresent()) {
            return;
        }
        for (final LegalDongCode code : SEED) {
            legalDongCodeRepository.save(code);
        }
        log.info("★ 법정동코드 대표 {}건 시드 완료 (전체는 행정안전부 데이터로 적재 필요) ★", SEED.size());
    }
}
