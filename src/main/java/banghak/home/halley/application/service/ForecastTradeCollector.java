package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.MonthlyTradeCacheRepository;
import banghak.home.halley.application.port.out.external.MinistryReferencePort;
import banghak.home.halley.config.VirtualThreadGate;
import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.reference.CachedDealType;
import banghak.home.halley.domain.reference.MonthlyTrades;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/** 가격 전망용 실거래를 모은다. */
@Slf4j
@Service
public class ForecastTradeCollector {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final MinistryReferencePort ministryReferencePort;
    private final MonthlyTradeCacheRepository cacheRepository;
    private final VirtualThreadGate gate;
    private final int lookbackMonths;
    private final int refetchRecentMonths;
    private final Duration refetchAfter;

    public ForecastTradeCollector(MinistryReferencePort ministryReferencePort,
                                  MonthlyTradeCacheRepository cacheRepository,
                                  @Qualifier("forecastGate") VirtualThreadGate gate,
                                  @Value("${forecast.lookback-months:60}") int lookbackMonths,
                                  @Value("${forecast.refetch-recent-months:3}") int refetchRecentMonths,
                                  @Value("${forecast.refetch-after-hours:24}") int refetchAfterHours) {
        this.ministryReferencePort = ministryReferencePort;
        this.cacheRepository = cacheRepository;
        this.gate = gate;
        this.lookbackMonths = lookbackMonths;
        this.refetchRecentMonths = refetchRecentMonths;
        this.refetchAfter = Duration.ofHours(refetchAfterHours);
    }

 /** 최근 lookbackMonths개월치 거래를 모은다. */
    public List<MonthlyTrades> collect(String lawdCd, CachedDealType dealType) {
        if (lawdCd == null || lawdCd.isBlank()) {
            log.info("Skipping forecast trade collection - no legal dong code. dealType={}", dealType);
            return List.of();
        }
        final YearMonth now = YearMonth.now();
        final List<YearMonth> months = new ArrayList<>(lookbackMonths);
        for (int i = 0; i < lookbackMonths; i++) {
            months.add(now.minusMonths(i));
        }

        final Map<YearMonth, MonthlyTrades> cached = cacheRepository.findAll(lawdCd, months, dealType);
        final List<YearMonth> toFetch = months.stream().filter(m -> needsFetch(m, cached.get(m), now)).toList();

        if (!toFetch.isEmpty()) {
            fetchAndStore(lawdCd, toFetch, dealType);
        }
        log.info("Forecast trades collected. lawdCd={}, dealType={}, months={}, cached={}, fetched={}",
                lawdCd, dealType, months.size(), months.size() - toFetch.size(), toFetch.size());

        final Map<YearMonth, MonthlyTrades> all = cacheRepository.findAll(lawdCd, months, dealType);
        return months.stream()
                .sorted()
                .map(all::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

 /** 다시 받아야 하는 달인지. */
    private boolean needsFetch(YearMonth month, MonthlyTrades cached, YearMonth now) {
        if (cached == null) {
            return true;
        }
        final boolean recent = month.isAfter(now.minusMonths(refetchRecentMonths));
        if (!recent) {
            return false;
        }
        return cached.fetchedAt() == null
                || Duration.between(cached.fetchedAt(), Instant.now()).compareTo(refetchAfter) > 0;
    }

    private void fetchAndStore(String lawdCd, List<YearMonth> months, CachedDealType dealType) {
        final List<Callable<MonthlyTrades>> tasks = months.stream()
                .map(month -> (Callable<MonthlyTrades>) () -> {
                    final String ym = month.format(YM);
                    final List<ReferenceTrade> trades = dealType == CachedDealType.JEONSE
                            ? ministryReferencePort.fetchJeonseDeposits(lawdCd, ym)
                            : ministryReferencePort.fetchTrades(lawdCd, ym);
                    if (trades == null) {
                        return null;
                    }
                    return new MonthlyTrades(lawdCd, month, dealType, trades, Instant.now());
                })
                .toList();

        int failed = 0;
        for (final MonthlyTrades monthly : gate.runAll(tasks)) {
            if (monthly == null) {
                failed++;
                continue;
            }
            cacheRepository.upsert(monthly);
        }
        if (failed > 0) {
            log.warn("Forecast trade months failed - will retry next run. lawdCd={}, dealType={}, failed={}/{}",
                    lawdCd, dealType, failed, months.size());
        }
    }
}
