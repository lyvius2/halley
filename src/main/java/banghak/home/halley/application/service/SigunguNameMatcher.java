package banghak.home.halley.application.service;

import banghak.home.halley.domain.geo.LegalDongCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 고시의 축약 지역명을 시군구 법정동코드로 맞춘다 (설계 I78).
 *
 * <p>고시는 시와 구를 붙여 줄여 씁니다 — {@code 화성동탄}(화성시 동탄구), {@code 성남분당}.
 * 어디서 잘라야 하는지 표시가 없어 분할로는 풀 수 없지만, <b>양쪽에서 접미사를 떼면 같아집니다.</b>
 *
 * <pre>
 *   화성동탄  → 화성동탄  =  화성동탄  ←  화성시 동탄구
 *   과천      → 과천      =  과천      ←  과천시
 *   강남구    → 강남      =  강남      ←  강남구
 * </pre>
 *
 * <p>서울은 `구`를 유지하고 경기는 안 하는 것처럼 보이지만, 양쪽 다 `시·군·구`와 공백을 떼면
 * 규칙이 하나입니다. 실물 고시 2건(각 40곳)에서 <b>40/40 매칭, 충돌 0</b>이었습니다.
 *
 * <p><b>시도 범위 안에서만 찾습니다.</b> {@code 중구}처럼 여러 시도에 있는 이름은 정규화하면
 * 겹치는데, 파서가 {@code 서울 중구}로 시도를 붙여 두므로 그 안에서 고르면 유일합니다.
 *
 * <p>LLM을 쓰지 않는 이유는 안전 때문입니다 — LLM은 모르는 지역에 대해 <b>그럴듯한 틀린 코드</b>를
 * 만들 수 있지만, 사전은 없으면 없다고 합니다.
 */
@Slf4j
@Component
public class SigunguNameMatcher {

    /** 행정구역 접미사와 공백. 이것만 떼면 고시 표기와 정식명칭이 같아진다. */
    private static final Pattern SUFFIX = Pattern.compile("[시군구\\s]");
    private static final int SIGUNGU_CODE_LENGTH = 5;

    /**
     * @param areaNames `서울 강남구` · `경기 화성동탄` 형식. 시도 접두어가 붙어 있어야 한다
     * @param dictionary 법정동코드 사전
     * @return 지역명 → 매칭 결과. <b>하나라도 못 찾으면 빈 맵</b>
     */
    public Map<String, Matched> match(List<String> areaNames, List<LegalDongCode> dictionary) {
        if (areaNames == null || areaNames.isEmpty()) {
            return Map.of();
        }
        if (dictionary == null || dictionary.isEmpty()) {
            log.warn("Cannot match regulated area names - the legal dong dictionary is empty. areas={}",
                    areaNames.size());
            return Map.of();
        }
        final Map<String, Map<String, LegalDongCode>> bySido = index(dictionary);
        final Map<String, Matched> matched = new LinkedHashMap<>();
        final List<String> missing = new java.util.ArrayList<>();
        for (final String areaName : areaNames) {
            find(areaName, bySido)
                    .ifPresentOrElse(m -> matched.put(areaName, m), () -> missing.add(areaName));
        }
        if (!missing.isEmpty()) {
            // 부분 매칭을 받아들이면 빠진 지역이 비규제(LTV 0.7)로 잡혀 한도가 과대평가된다.
            // 값이 있으니 맞는 줄 알게 되어 아무것도 없는 것보다 위험하다
            log.error("Regulated area names unmatched - discarding all {} matches. unmatched={}",
                    matched.size(), missing);
            return Map.of();
        }
        return matched;
    }

    private Optional<Matched> find(String areaName, Map<String, Map<String, LegalDongCode>> bySido) {
        final int space = areaName == null ? -1 : areaName.indexOf(' ');
        if (space <= 0) {
            return Optional.empty();
        }
        final String key = normalize(areaName.substring(space + 1));
        return resolveSido(areaName.substring(0, space), bySido.keySet())
                .map(bySido::get)
                .map(names -> names.get(key))
                .map(code -> new Matched(
                        code.code().substring(0, SIGUNGU_CODE_LENGTH),
                        code.sido() + " " + code.sigungu()));
    }

    /**
     * 고시의 짧은 시도 표기를 사전의 정식명칭에 맞춘다 — `서울` → `서울특별시`.
     *
     * <p><b>별칭표를 두지 않습니다.</b> 시도 이름도 바뀝니다(광주광역시와 전라남도가
     * `전남광주통합특별시`로 통합됐습니다). 박아 두면 낡아도 낡은 줄 모르므로 사전에서 찾습니다.
     *
     * <p>여러 개에 걸리면 <b>고르지 않습니다.</b> 잘못 고르면 엉뚱한 시도의 같은 이름 구가
     * 규제지역이 되는데, 그건 값이 없는 것보다 위험합니다.
     */
    private Optional<String> resolveSido(String token, Set<String> candidates) {
        final String trimmed = token.trim();
        if (candidates.contains(trimmed)) {
            return Optional.of(trimmed);
        }
        final List<String> matched = candidates.stream()
                .filter(name -> name.startsWith(trimmed) || name.contains(trimmed))
                .toList();
        if (matched.size() != 1) {
            log.warn("Cannot resolve sido from notice. token={}, candidates={}", trimmed, matched);
            return Optional.empty();
        }
        return Optional.of(matched.getFirst());
    }

    /** 시도별로 `정규화된 시군구명 → 코드` 색인을 만든다. */
    private Map<String, Map<String, LegalDongCode>> index(List<LegalDongCode> dictionary) {
        final Map<String, Map<String, LegalDongCode>> bySido = new HashMap<>();
        for (final LegalDongCode entry : dictionary) {
            if (entry.sido() == null || entry.sigungu() == null
                    || entry.code() == null || entry.code().length() < SIGUNGU_CODE_LENGTH) {
                continue;
            }
            bySido.computeIfAbsent(entry.sido(), k -> new HashMap<>())
                    .putIfAbsent(normalize(entry.sigungu()), entry);
        }
        return bySido;
    }

    private String normalize(String name) {
        return SUFFIX.matcher(name.trim()).replaceAll("");
    }

    /** @param code 법정동코드 앞 5자리 */
    public record Matched(String code, String name) {
    }
}
