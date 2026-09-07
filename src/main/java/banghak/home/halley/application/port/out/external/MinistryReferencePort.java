package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.property.ReferenceTrade;

import java.util.List;

public interface MinistryReferencePort {

 /** null과 빈 목록은 뜻이 다릅니다. */
    List<ReferenceTrade> fetchTrades(String lawdCd, String dealYmd);

 /** 같은 단지의 순수 전세 거래. */
    default List<ReferenceTrade> fetchJeonseDeposits(String lawdCd, String dealYmd) {
        return List.of();
    }
}
