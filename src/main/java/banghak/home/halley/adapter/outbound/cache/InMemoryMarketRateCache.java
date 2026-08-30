package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.MarketRateCache;
import banghak.home.halley.domain.finance.LoanProductType;
import banghak.home.halley.domain.finance.MarketRate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** local용 인메모리 구현 (설계 I81). */
@Component
@Profile("!live")
public class InMemoryMarketRateCache implements MarketRateCache {

    /** 공시는 월 단위로 바뀌므로 하루면 충분히 짧다. */
    private static final Duration TTL = Duration.ofDays(1);

    private final Map<LoanProductType, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Optional<MarketRate> get(LoanProductType type) {
        final Entry entry = store.get(type);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            store.remove(type);
            return Optional.empty();
        }
        return Optional.of(entry.rate());
    }

    @Override
    public void put(MarketRate rate) {
        store.put(rate.type(), new Entry(rate, Instant.now().plus(TTL)));
    }

    private record Entry(MarketRate rate, Instant expiresAt) {
    }
}
