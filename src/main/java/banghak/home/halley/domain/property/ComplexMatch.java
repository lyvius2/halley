package banghak.home.halley.domain.property;

import java.util.Optional;

public final class ComplexMatch {

    private ComplexMatch() {
    }

    public static boolean same(String address, String myName, ReferenceTrade trade) {
        if (trade == null) {
            return false;
        }
        final Optional<JibunAddress> mine = JibunAddress.of(address);
        final Optional<JibunAddress> theirs = trade.lot();
        if (mine.isPresent() && theirs.isPresent()) {
            if (!mine.get().sameDong(theirs.get())) {
                // 동이 다르면 다른 단지다 — 이름을 볼 것도 없다
                return false;
            }
            if (mine.get().sameLot(theirs.get())) {
                // 같은 자리다. 이름이 달라도 같은 단지다 — 브랜드가 바뀌었을 뿐이다
                return true;
            }
            // 동은 같고 번지가 다르다. 대단지는 번지가 여럿이라 이름으로 판단한다
        }
        return sameName(myName, trade.apartmentName());
    }

    public static boolean sameName(String myName, String theirName) {
        if (!ComplexName.comparable(myName, theirName)) {
            return true;
        }
        return ComplexName.same(myName, theirName);
    }

    /** 이 거래가 주소로 확인됐는가 — 화면이 "왜 잡혔는지" 말할 때 쓴다  */
    public static boolean matchedByLot(String address, ReferenceTrade trade) {
        if (trade == null) {
            return false;
        }
        final Optional<JibunAddress> mine = JibunAddress.of(address);
        final Optional<JibunAddress> theirs = trade.lot();
        return mine.isPresent() && theirs.isPresent() && mine.get().sameLot(theirs.get());
    }
}
