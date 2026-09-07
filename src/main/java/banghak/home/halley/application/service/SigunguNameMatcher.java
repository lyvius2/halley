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

/** 고시의 축약 지역명을 시군구 법정동코드로 맞춘다. */
@Slf4j
@Component
public class SigunguNameMatcher {

 /** 행정구역 접미사와 공백. 이것만 떼면 고시 표기와 정식명칭이 같아진다. */
    private static final Pattern SUFFIX = Pattern.compile("[시군구\\s]");
    private static final int SIGUNGU_CODE_LENGTH = 5;


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

 /** 고시의 짧은 시도 표기를 사전의 정식명칭에 맞춘다. 서울 → 서울특별시. */
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

 /** 시도별로 정규화된 시군구명 → 코드 색인을 만든다. */
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


    public record Matched(String code, String name) {
    }
}
