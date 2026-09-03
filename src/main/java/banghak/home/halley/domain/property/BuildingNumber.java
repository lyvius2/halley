package banghak.home.halley.domain.property;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * `동/호` 에서 <b>동만</b> 뽑는다 (설계 I268).
 *
 * <pre>
 * 102동          →  102동
 * 102동 1503호   →  102동
 * 가동           →  가동
 * 1503호         →  (없음)   ← 동을 모르면 건물을 못 가린다
 * </pre>
 *
 * <h4>왜 호를 버리는가</h4>
 *
 * <p>카카오 장소검색에 <b>호까지 붙이면 결과가 없습니다.</b> 실제로 확인했습니다 —
 * `한화포레나정릉 102동` 은 나오고 `한화포레나정릉 102동 1503호` 는 안 나옵니다.
 * 지도에 있는 것은 <b>건물</b>이지 세대가 아닙니다.
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
