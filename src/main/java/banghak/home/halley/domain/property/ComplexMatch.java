package banghak.home.halley.domain.property;

import java.util.Optional;

/**
 * 이 실거래가 이 매물의 것인가.
 *
 * 이름만으로는 못 가리는 단지가 있다
 *
 *
 * 상계주공7단지  ↔  상계주공7(고층)     괄호를 버려 맞춘다 (I230)
 * 한화포레나정릉 ↔  정릉꿈에그린        글자가 하나도 안 겹친다  ← 이것
 *
 *
 * 브랜드가 바뀐 단지입니다. 대우 `푸르지오` → 한화 `꿈에그린` → `포레나` 로
 * 이어져, 국토부에 옛 이름으로 남아 있으면 부분 문자열 비교로는 영원히
 * 안 맞습니다.
 *
 * 주소가 이름보다 확실하다
 *
 *
 * 동이 다르다            →  다른 단지다 (이름을 볼 것도 없다)
 * 동·번지가 같다         →  같은 단지다 (이름이 달라도)
 * 동은 같고 번지가 다르다 →  이름으로 판단한다
 *
 *
 * 번지 불일치를 배제 근거로 쓰지 않습니다. 번지가 여러 개인 대단지가
 * 있어서, 다르다고 곧바로 빼면 멀쩡한 거래를 잃습니다.
 * 번지 일치는 이름을 이기는 근거로만 씁니다.
 *
 * 규칙은 여기 하나다
 *
 * 에서 같은 규칙이 두 벌이라 전망이 늘 자료 부족이었습니다.
 * 전망과 실거래 카드가 이것만 부릅니다.
 */
public final class ComplexMatch {

    private ComplexMatch() {
    }

    /**
     * 같은 단지로 볼 것인가.
     *
     * @param address 매물의 지번주소. 없으면 이름으로만 가린다
     */
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

    /**
     * 이름만으로 가린다.
     *
     * 둘 중 하나라도 이름을 못 가리면 같다고 봅니다 — 같은 법정동이라
     * 아주 틀리진 않고, 여기서 빼면 이름이 없는 거래를 통째로 잃습니다.
     */
    public static boolean sameName(String myName, String theirName) {
        if (!ComplexName.comparable(myName, theirName)) {
            return true;
        }
        return ComplexName.same(myName, theirName);
    }

    /** 이 거래가 주소로 확인됐는가 — 화면이 "왜 잡혔는지" 말할 때 쓴다 */
    public static boolean matchedByLot(String address, ReferenceTrade trade) {
        if (trade == null) {
            return false;
        }
        final Optional<JibunAddress> mine = JibunAddress.of(address);
        final Optional<JibunAddress> theirs = trade.lot();
        return mine.isPresent() && theirs.isPresent() && mine.get().sameLot(theirs.get());
    }
}
