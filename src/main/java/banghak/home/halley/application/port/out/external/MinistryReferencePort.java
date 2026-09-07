package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.property.ReferenceTrade;

import java.util.List;

public interface MinistryReferencePort {

    /**
     * null과 빈 목록은 뜻이 다릅니다.
     *
     *
     * null       조회 실패 (429·타임아웃·키 없음) — 모르는 것
     * List.of()  그 달에 거래가 없었다      — 아는 것
     *
     *
     * 둘을 같이 두면 실패가 "거래 0건"으로 캐시에 굳습니다. 과거 달은 다시 받지
     * 않으므로(I128) 한 번 굳으면 영영 구멍인 채로 남고, 그 구멍 위에서 중앙값이 계산됩니다.
     * 실제로 429를 맞고 그렇게 될 뻔했습니다.
     */
    List<ReferenceTrade> fetchTrades(String lawdCd, String dealYmd);

    /**
     * 같은 단지의 순수 전세 거래.
     *
     * 전세가율(전세 보증금 / 매매가)을 내려는 것입니다.
     *
     * 돌려주는 `dealAmount`는 매매가가 아니라 보증금입니다. 형태가 같아
     * `ReferenceTrade`를 그대로 쓰지만 뜻이 다릅니다 — 섞어 쓰지 마십시오.
     *
     * 월세가 붙은 반전세는 뺍니다. 보증금이 낮게 잡혀 전세가율을 왜곡합니다.
     * 이 앱은 월세를 취급하지 않습니다.
     *
     * null과 빈 목록의 구분은 {@link #fetchTrades}와 같습니다.
     *
     * 기본값은 빈 목록입니다 — 이 인터페이스를 람다로 대신하는 테스트가 여럿이라
     * 메서드를 늘리면 그 전부가 깨집니다. 실제 어댑터는 재정의합니다.
     */
    default List<ReferenceTrade> fetchJeonseDeposits(String lawdCd, String dealYmd) {
        return List.of();
    }
}
