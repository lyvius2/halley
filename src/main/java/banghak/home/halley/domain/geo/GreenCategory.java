package banghak.home.halley.domain.geo;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** 녹색환경(GREEN) 3종 판정. 카카오 응답의 장소명이 아니라 category_name으로 분류한다. */
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
