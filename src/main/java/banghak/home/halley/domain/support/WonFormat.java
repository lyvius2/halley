package banghak.home.halley.domain.support;

/** 금액을 억·만원으로 읽기 쉽게. */
public final class WonFormat {

    private WonFormat() {
    }

    public static String of(long amount) {
        final long eok = amount / 100_000_000L;
        final long man = (amount % 100_000_000L) / 10_000L;
        if (eok > 0 && man > 0) {
            return String.format("%d억 %,d만원", eok, man);
        }
        if (eok > 0) {
            return eok + "억원";
        }
        return String.format("%,d만원", man);
    }
}
