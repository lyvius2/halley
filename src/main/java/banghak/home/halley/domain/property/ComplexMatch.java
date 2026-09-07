package banghak.home.halley.domain.property;

import java.util.Optional;

/** 이 실거래가 이 매물의 것인가. */
public final class ComplexMatch {

    private ComplexMatch() {
    }

 /** 같은 단지로 볼 것인가. */
    public static boolean same(String address, String myName, ReferenceTrade trade) {
        if (trade == null) {
            return false;
        }
        final Optional<JibunAddress> mine = JibunAddress.of(address);
        final Optional<JibunAddress> theirs = trade.lot();
        if (mine.isPresent() && theirs.isPresent()) {
            if (!mine.get().sameDong(theirs.get())) {
                return false;
            }
            if (mine.get().sameLot(theirs.get())) {
                return true;
            }
        }
        return sameName(myName, trade.apartmentName());
    }

 /** 이름만으로 가린다. */
    public static boolean sameName(String myName, String theirName) {
        if (!ComplexName.comparable(myName, theirName)) {
            return true;
        }
        return ComplexName.same(myName, theirName);
    }

 /** 이 거래가 주소로 확인됐는가. 화면이 "왜 잡혔는지" 말할 때 쓴다 */
    public static boolean matchedByLot(String address, ReferenceTrade trade) {
        if (trade == null) {
            return false;
        }
        final Optional<JibunAddress> mine = JibunAddress.of(address);
        final Optional<JibunAddress> theirs = trade.lot();
        return mine.isPresent() && theirs.isPresent() && mine.get().sameLot(theirs.get());
    }
}
