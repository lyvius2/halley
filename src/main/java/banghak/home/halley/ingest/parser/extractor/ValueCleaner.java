package banghak.home.halley.ingest.parser.extractor;

import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * 라벨 값에 붙어 오는 군더더기를 떼는 규칙 (설계 I82).
 *
 * <p>같은 항목인데 페이지마다 표기가 다릅니다 — 어떤 매물은 `3/2`, 어떤 매물은 `3/2개`로
 * 옵니다. 그대로 저장하면 <b>같은 값이 두 모양으로 남아</b> 비교와 표시가 어긋납니다.
 */
public final class ValueCleaner {

    /** `3/2개` → `3/2`. 개수 단위는 값이 아니라 표기다. */
    public static final UnaryOperator<String> ROOM_BATH =
            value -> value == null ? null : value.replaceAll("개\\s*$", "").trim();

    /** `(거실 기준) 남동향` → `남동향`. 괄호 주석은 어느 방향인지와 무관하다. */
    private static final Pattern LEADING_NOTE = Pattern.compile("^\\s*\\([^)]*\\)\\s*");
    public static final UnaryOperator<String> DIRECTION =
            value -> value == null ? null : LEADING_NOTE.matcher(value).replaceFirst("").trim();

    private ValueCleaner() {
    }
}
