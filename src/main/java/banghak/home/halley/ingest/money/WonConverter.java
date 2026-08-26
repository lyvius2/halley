package banghak.home.halley.ingest.money;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WonConverter {

    private static final Pattern WON = Pattern.compile(
            "^\\s*(?:(\\d+)\\s*억)?\\s*(?:([\\d,]+)\\s*만(?:원)?)?\\s*(?:([\\d,]+)\\s*원)?\\s*$");

    private WonConverter() {
    }

    /**
     * "15억" → 1,500,000,000 / "13억 5,000만원" → 1,350,000,000
     * "4,950만원" → 49,500,000 / "17만 4,081원" → 174,081. 금액 패턴이 없으면 null.
     */
    public static Long toWon(String raw) {
        if (raw == null) {
            return null;
        }
        final Matcher matcher = WON.matcher(raw);
        if (!matcher.matches()) {
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
