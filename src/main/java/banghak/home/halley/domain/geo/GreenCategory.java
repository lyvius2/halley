package banghak.home.halley.domain.geo;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 녹색환경(`GREEN`) 3종 판정. 카카오 응답의 <b>장소명이 아니라 `category_name`</b>으로 분류한다.
 * 장소명 매칭은 "떡산 롯데백화점"·"산과맥주"를 산으로, "달빛어린이공원 개방화장실"을 공원으로 잡는다.
 *
 * <p>실측한 `category_name` 형태:
 * <ul>
 *   <li>공원 — {@code 여행 > 공원}, {@code 여행 > 공원 > 도시근린공원}</li>
 *   <li>산 — {@code 여행 > 관광,명소 > 산}, {@code 여행 > 관광,명소 > 자연휴양림}, {@code 여행 > 관광,명소 > 숲}</li>
 *   <li>하천 — {@code 여행 > 관광,명소 > 하천}</li>
 * </ul>
 * 세그먼트 단위로 정확히 비교하므로 {@code 여행 > 공원시설물}(음수대)이나 {@code 가정,생활 > 화장실}은 제외된다.
 */
public enum GreenCategory {

    PARK(List.of("공원")),
    MOUNTAIN(List.of("산", "자연휴양림", "숲")),
    RIVER(List.of("하천", "강"));

    private final List<String> segments;

    GreenCategory(List<String> segments) {
        this.segments = segments;
    }

    public static Optional<GreenCategory> classify(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return Optional.empty();
        }
        final List<String> parts = Arrays.stream(categoryName.split(">"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
        return Arrays.stream(values())
                .filter(category -> category.matches(parts))
                .findFirst();
    }

    private boolean matches(List<String> parts) {
        return parts.stream().anyMatch(segments::contains);
    }
}
