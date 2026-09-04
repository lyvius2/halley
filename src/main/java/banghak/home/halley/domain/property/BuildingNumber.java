package banghak.home.halley.domain.property;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * `동/호` 에서 동만 뽑는다 (설계 I268) — 카카오 장소검색은 호까지 붙이면 결과가 없다.
 * 지도에 있는 것은 건물이지 세대가 아니다.
 */
public final class BuildingNumber {

    /** 공백이 아닌 토막 중 `동` 으로 끝나는 첫 번째. */
    private static final Pattern DONG = Pattern.compile("(?<![\\p{L}\\p{N}])([\\p{L}\\p{N}]{1,10}동)(?![\\p{L}\\p{N}])");

    public static Optional<String> of(String dongHo) {
        if (dongHo == null || dongHo.isBlank()) {
            return Optional.empty();
        }
        final Matcher matcher = DONG.matcher(dongHo.trim());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private BuildingNumber() {
    }
}
