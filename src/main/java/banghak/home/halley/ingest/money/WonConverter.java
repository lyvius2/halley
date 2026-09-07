package banghak.home.halley.ingest.money;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WonConverter {

    private static final Pattern WON = Pattern.compile(
            "^\\s*(?:(\\d+)\\s*억(?:원)?)?\\s*(?:([\\d,]+)\\s*만(?:원)?)?\\s*(?:([\\d,]+)\\s*원)?\\s*$");

 /** 금액으로 시작하는 글에서 그 금액만. */
    private static final Pattern LEADING_WON = Pattern.compile(
            "^\\s*(?:(\\d+)\\s*억(?:원)?)?\\s*(?:([\\d,]+)\\s*만(?:원)?)?\\s*(?:([\\d,]+)\\s*원)?");

    private WonConverter() {
    }

 /** 금액으로 시작하면 그 금액. 뒤에 뭐가 붙어 있어도 된다. */
    public static Long leadingWon(String raw) {
        return raw == null ? null : sum(LEADING_WON.matcher(raw), false);
    }

 /** "15억" → 1,500,000,000 / "13억 5,000만원" → 1,350,000,000 */
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
