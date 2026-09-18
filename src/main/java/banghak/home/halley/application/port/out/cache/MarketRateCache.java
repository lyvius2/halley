package banghak.home.halley.application.port.out.cache;

import banghak.home.halley.domain.finance.LoanProductType;
import banghak.home.halley.domain.finance.MarketRate;

import java.util.Optional;

public interface MarketRateCache {

    Optional<MarketRate> get(LoanProductType type);

    void put(MarketRate rate);
}
