package banghak.home.halley.ingest.money;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WonConverter {

    private static final Pattern WON = Pattern.compile(
            "^\\s*(?:(\\d+)\\s*억(?:원)?)?\\s*(?:([\\d,]+)\\s*만(?:원)?)?\\s*(?:([\\d,]+)\\s*원)?\\s*$");

    /**
     * 금액으로 <b>시작</b>하는 글에서 그 금액만 (설계 I283).
     *
     * <p>라벨과 값이 한 줄에 붙어 오면 값 뒤에 다른 것도 붙어 옵니다.
     *
     * <pre>
     * KB시세 7억 3,000만원투기과열LTV 40%
     *        └─── 여기까지가 값 ───┘
     * </pre>
     *
     * <p>{@link #toWon} 은 <b>줄 전체가 금액일 때만</b> 읽습니다. 그건 그대로 둡니다 —
     * 느슨하게 바꾸면 아무 문장에서나 숫자를 집습니다. 붙어 오는 자리에서만 이것을 씁니다.
     */
    private static final Pattern LEADING_WON = Pattern.compile(
            "^\\s*(?:(\\d+)\\s*억(?:원)?)?\\s*(?:([\\d,]+)\\s*만(?:원)?)?\\s*(?:([\\d,]+)\\s*원)?");

    private WonConverter() {
    }

    /** 금액으로 시작하면 그 금액. 뒤에 뭐가 붙어 있어도 된다 (설계 I283). */
    public static Long leadingWon(String raw) {
        return raw == null ? null : sum(LEADING_WON.matcher(raw), false);
    }

    /**
     * "15억" → 1,500,000,000 / "13억 5,000만원" → 1,350,000,000
     * "4,950만원" → 49,500,000 / "17만 4,081원" → 174,081. 금액 패턴이 없으면 null.
     */
    public static Long toWon(String raw) {
        return raw == null ? null : sum(WON.matcher(raw), true);
    }

    private static Long sum(Matcher matcher, boolean whole) {
        final boolean hit = whole ? matcher.matches() : matcher.find();
        if (!hit) {
            return null;
        }
        long won = 0L;
        boolean found = false;
        if (matcher.group(1) != null) {
            won += Long.parseLong(matcher.group(1)) * 100_000_000L;
            found = true;
        }
        if (matcher.group(2) != null) {
            won += Long.parseLong(matcher.group(2).replace(",", "")) * 10_000L;
            found = true;
        }
        if (matcher.group(3) != null) {
            won += Long.parseLong(matcher.group(3).replace(",", ""));
            found = true;
        }
        return found ? won : null;
    }
}
