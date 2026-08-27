package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.LegalDongCodeRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 지번주소에서 시군구·동을 추출해 법정동코드(시군구 5자리 LAWD_CD)로 역매핑한다 (설계 5.5).
 */
@Service
public class LegalDongCodeService {

    private static final Pattern DONG_PATTERN = Pattern.compile("(\\S+구)\\s+(\\S+?(?:동|읍|면))");

    private final LegalDongCodeRepository legalDongCodeRepository;

    public LegalDongCodeService(LegalDongCodeRepository legalDongCodeRepository) {
        this.legalDongCodeRepository = legalDongCodeRepository;
    }

    public Optional<String> deriveSigunguCode(String addressJibun) {
        if (addressJibun == null || addressJibun.isBlank()) {
            return Optional.empty();
        }
        final Matcher matcher = DONG_PATTERN.matcher(addressJibun);
        if (!matcher.find()) {
            return Optional.empty();
        }
        final String sigungu = matcher.group(1);
        final String dong = matcher.group(2);
        return legalDongCodeRepository.findBySigunguAndDong(sigungu, dong)
                .map(code -> code.code().length() >= 5 ? code.code().substring(0, 5) : code.code());
    }
}
