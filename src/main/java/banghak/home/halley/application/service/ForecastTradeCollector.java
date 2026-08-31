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

/**
 * 가격 전망용 실거래를 모은다 (설계 I129).
 *
 * <p>실거래 카드(`ReferenceTransactionService`)와 <b>다른 목적</b>입니다. 그쪽은 매물 하나의
 * 최근 시세를 보여 주려고 12개월을 훑고 단지·면적으로 걸러 저장합니다.
 * 여기는 <b>추세를 재려고</b> 60개월을 훑고 <b>거르지 않고</b> 캐시에 담습니다.
 *
 * <p>거르는 일은 지표 계산이 맡습니다 — 캐시에 걸러 담으면 같은 법정동의 다른 매물이
 * 재사용하지 못합니다.
 */
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

    /**
     * 최근 {@code lookbackMonths}개월치 거래를 모은다.
     *
     * <p>캐시에 있는 달은 <b>부르지 않습니다.</b> 같은 법정동의 두 번째 매물부터는
     * 거의 호출이 없습니다.
     *
     * @return 오래된 달부터 정렬된 목록. 못 받은 달은 빠진다
     */
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

        // 갱신분을 포함해 다시 읽는다 — 방금 받은 것과 캐시에 있던 것을 합치는 것보다 단순하다
        final Map<YearMonth, MonthlyTrades> all = cacheRepository.findAll(lawdCd, months, dealType);
        return months.stream()
                .sorted()
                .map(all::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * 다시 받아야 하는 달인지.
     *
     * <p><b>과거 달은 바뀌지 않습니다.</b> 국토부 신고 지연 때문에 최근 몇 달만 다시 받고,
     * 그 이전은 한 번 받으면 끝입니다 — 60개월 중 57개월이 그렇습니다.
     */
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
                        // 조회 실패는 저장하지 않는다 (설계 I140). 여기서 빈 목록으로 담으면
                        // '거래 0건'으로 굳고, 과거 달은 다시 받지 않으므로 영영 구멍이 된다
                        return null;
                    }
                    // 거래가 없는 달은 저장한다 — '아직 안 받은 달'과 구분되지 않으면
                    // 매번 다시 부른다 (설계 I128)
                    return new MonthlyTrades(lawdCd, month, dealType, trades, Instant.now());
                })
                .toList();

        int failed = 0;
        for (final MonthlyTrades monthly : gate.runAll(tasks)) {
            if (monthly == null) {
                // 한 달이 실패해도 나머지는 저장한다. 담지 않았으므로 다음 실행에서 그 달만 다시 받는다
                failed++;
                continue;
            }
            cacheRepository.upsert(monthly);
        }
        if (failed > 0) {
            // 몇 달이 비었는지 알아야 전망을 얼마나 믿을지 판단할 수 있다
            log.warn("Forecast trade months failed - will retry next run. lawdCd={}, dealType={}, failed={}/{}",
                    lawdCd, dealType, failed, months.size());
        }
    }
}
