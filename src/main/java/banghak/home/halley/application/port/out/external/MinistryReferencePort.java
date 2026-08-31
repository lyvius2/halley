package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.property.ReferenceTrade;

import java.util.List;

public interface MinistryReferencePort {

    List<ReferenceTrade> fetchTrades(String lawdCd, String dealYmd);

    /**
     * 같은 단지의 <b>순수 전세</b> 거래 (설계 I131).
     *
     * <p>전세가율(전세 보증금 / 매매가)을 내려는 것입니다.
     *
     * <p><b>돌려주는 `dealAmount`는 매매가가 아니라 보증금입니다.</b> 형태가 같아
     * `ReferenceTrade`를 그대로 쓰지만 뜻이 다릅니다 — 섞어 쓰지 마십시오.
     *
     * <p><b>월세가 붙은 반전세는 뺍니다.</b> 보증금이 낮게 잡혀 전세가율을 왜곡합니다.
     * 이 앱은 월세를 취급하지 않습니다(설계 I94).
     *
     * <p>기본값은 빈 목록입니다 — 이 인터페이스를 <b>람다로 대신하는 테스트</b>가 여럿이라
     * 메서드를 늘리면 그 전부가 깨집니다. 실제 어댑터는 재정의합니다.
     */
    default List<ReferenceTrade> fetchJeonseDeposits(String lawdCd, String dealYmd) {
        return List.of();
    }
}
