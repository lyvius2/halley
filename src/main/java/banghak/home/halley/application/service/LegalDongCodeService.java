package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.LegalDongCodeRepository;
import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.geo.LegalDongCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 지번주소에서 법정동코드(시군구 5자리 `LAWD_CD`)를 역매핑한다 (설계 5.5 · I43).
 *
 * <p>참조 테이블을 4만 건 적재하는 대신 <b>카카오 주소검색의 `b_code`</b>를 쓴다. 이미 쓰고 있는
 * 연동이라 새 API 키가 필요 없고, 전국 주소에 대해 10자리 법정동코드를 그대로 돌려준다.
 * 조회 결과는 `legal_dong_code`에 캐시해 같은 동을 다시 묻지 않는다.
 */
@Slf4j
@Service
public class LegalDongCodeService {

    private static final Pattern DONG_PATTERN = Pattern.compile("(\\S+구)\\s+(\\S+?(?:동|읍|면))");
    /** "서울 노원구 상계동 771" → "서울 노원구 상계동" — 존재하지 않는 번지로 조회가 실패할 때 쓴다. */
    private static final Pattern UP_TO_DONG = Pattern.compile("^.*?(?:동|읍|면)(?=\\s|$)");
    private static final Pattern TRAILING_BUNJI = Pattern.compile("\\s+\\d+(-\\d+)?$");
    private static final int SIGUNGU_CODE_LENGTH = 5;

    private final LegalDongCodeRepository legalDongCodeRepository;
    private final KakaoLocalPort kakaoLocalPort;

    public LegalDongCodeService(LegalDongCodeRepository legalDongCodeRepository,
                                KakaoLocalPort kakaoLocalPort) {
        this.legalDongCodeRepository = legalDongCodeRepository;
        this.kakaoLocalPort = kakaoLocalPort;
    }

    public Optional<String> deriveSigunguCode(String addressJibun) {
        if (addressJibun == null || addressJibun.isBlank()) {
            return Optional.empty();
        }
        return fromTable(addressJibun)
                .or(() -> fromKakao(addressJibun))
                .map(LegalDongCodeService::toSigunguCode);
    }

    private Optional<String> fromTable(String addressJibun) {
        final Matcher matcher = DONG_PATTERN.matcher(addressJibun);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return legalDongCodeRepository.findBySigunguAndDong(matcher.group(1), matcher.group(2))
                .map(LegalDongCode::code);
    }

    /** 전체 주소로 먼저 묻고, 번지가 실제로 없어 실패하면 동까지만 잘라 다시 묻는다. */
    private Optional<String> fromKakao(String addressJibun) {
        final Optional<String> exact = searchLegalDongCode(addressJibun);
        if (exact.isPresent()) {
            cache(addressJibun, exact.get());
            return exact;
        }
        final String withoutBunji = stripBunji(addressJibun);
        if (withoutBunji.equals(addressJibun)) {
            return Optional.empty();
        }
        final Optional<String> fallback = searchLegalDongCode(withoutBunji);
        fallback.ifPresent(code -> cache(addressJibun, code));
        return fallback;
    }

    /** 키 미설정·외부 장애는 예외로 올리지 않고 빈 값으로 흡수한다(실거래가는 참고 정보이므로 — INTERFACE_MANUAL 6.1). */
    private Optional<String> searchLegalDongCode(String query) {
        try {
            final List<GeoSearchResult> results = kakaoLocalPort.searchAddress(query);
            return results.stream()
                    .map(GeoSearchResult::legalDongCode)
                    .filter(code -> code != null && code.length() >= SIGUNGU_CODE_LENGTH)
                    .findFirst();
        } catch (RuntimeException e) {
            log.warn("카카오 주소검색으로 법정동코드를 조회하지 못했습니다. query={}, cause={}", query, e.getMessage());
            return Optional.empty();
        }
    }

    static String stripBunji(String addressJibun) {
        final Matcher matcher = UP_TO_DONG.matcher(addressJibun);
        if (matcher.find()) {
            return matcher.group();
        }
        return TRAILING_BUNJI.matcher(addressJibun).replaceFirst("");
    }

    private void cache(String addressJibun, String code) {
        final Matcher matcher = DONG_PATTERN.matcher(addressJibun);
        if (!matcher.find() || legalDongCodeRepository.findById(code).isPresent()) {
            return;
        }
        legalDongCodeRepository.save(new LegalDongCode(
                code, null, matcher.group(1), matcher.group(2), null, true, Instant.now()));
        log.info("법정동코드를 카카오 주소검색으로 확보해 캐시했습니다. {} {} → {}",
                matcher.group(1), matcher.group(2), code);
    }

    private static String toSigunguCode(String code) {
        return code.length() >= SIGUNGU_CODE_LENGTH ? code.substring(0, SIGUNGU_CODE_LENGTH) : code;
    }
}
