package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.property.ReferenceTrade;

import java.util.List;

public interface MinistryReferencePort {

    List<ReferenceTrade> fetchTrades(String lawdCd, String dealYmd);

    default List<ReferenceTrade> fetchJeonseDeposits(String lawdCd, String dealYmd) {
        return List.of();
    }
}
