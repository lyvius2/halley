package banghak.home.halley.ingest.parser.extractor;

import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public final class ValueCleaner {

    /** `3/2개` → `3/2`. 개수 단위는 값이 아니라 표기다.  */
    public static final UnaryOperator<String> ROOM_BATH =
            value -> value == null ? null : value.replaceAll("개\\s*$", "").trim();

    /** `(거실 기준) 남동향` → `남동향`. 괄호 주석은 어느 방향인지와 무관하다.  */
    private static final Pattern LEADING_NOTE = Pattern.compile("^\\s*\\([^)]*\\)\\s*");
    public static final UnaryOperator<String> DIRECTION =
            value -> value == null ? null : LEADING_NOTE.matcher(value).replaceFirst("").trim();

    private ValueCleaner() {
    }
}
