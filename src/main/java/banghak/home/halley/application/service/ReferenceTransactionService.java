package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.ReferenceCardResponse;
import banghak.home.halley.adapter.inbound.web.dto.ReferenceTransactionResponse;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.ReferenceTransactionRepository;
import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.application.port.out.external.MinistryReferencePort;
import banghak.home.halley.config.VirtualThreadGate;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.domain.property.Complex;
import banghak.home.halley.domain.property.ComplexMatch;
import banghak.home.halley.domain.property.JibunAddress;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.ReferenceDealType;
import banghak.home.halley.domain.property.ReferenceSource;
import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.property.ReferenceTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReferenceTransactionService {

 /** 같은 타입으로 볼 전용면적 오차. */
 /** 이번 달은 건너뛴다. */
    private static final int REPORTING_LAG_MONTHS = 1;
 /** 저장할 최대 건수. 담보가치는 중앙값을 쓰므로 이만큼이면 넉넉하다. */
    private static final int MAX_SAVED = 50;

    private static final double AREA_TOLERANCE = 0.15;
 /** 이보다 짧은 단지명은 우연히 걸린다. 판정에 쓰지 않는다. */
    private static final int MIN_NAME_LENGTH = 2;

    private final PropertyRepository propertyRepository;
    private final PropertyAccessGuard propertyAccessGuard;
    private final ReferenceTransactionRepository referenceTransactionRepository;
    private final MinistryReferencePort ministryReferencePort;
    private final LegalDongCodeService legalDongCodeService;
 /** 거슬러 볼 개월 수. */
    private final int lookbackMonths;
 /** 배경 조회를 다른 보정과 같은 줄에 세운다. */
    private final VirtualThreadGate gate;
    private final CachePort cache;
 /** 실거래는 매물이 아니라 단지와 평형에 붙는다. */
    private final ComplexService complexService;

 /** 헛걸음을 기억해 두는 시간. */
    private static final Duration MISS_TTL = Duration.ofHours(24);

 /** 배경 조회가 돌고 있다는 표시의 수명. */
    private static final Duration LOOKING_TTL = Duration.ofMinutes(3);

 /** 못 찾은 게 아니라 못 찾아본 경우의 수명. */
    private static final Duration BLOCKED_TTL = Duration.ofMinutes(10);

    public ReferenceTransactionService(PropertyAccessGuard propertyAccessGuard,
                                  PropertyRepository propertyRepository,
                                       ReferenceTransactionRepository referenceTransactionRepository,
                                       MinistryReferencePort ministryReferencePort,
                                       LegalDongCodeService legalDongCodeService,
                                       @Value("${ministry.reference.lookback-months:12}")
                                       int lookbackMonths,
                                       VirtualThreadGate gate,
                                       ComplexService complexService,
                                       CachePort cache) {
        this.propertyAccessGuard = propertyAccessGuard;
        this.propertyRepository = propertyRepository;
        this.referenceTransactionRepository = referenceTransactionRepository;
        this.ministryReferencePort = ministryReferencePort;
        this.legalDongCodeService = legalDongCodeService;
        this.lookbackMonths = lookbackMonths;
        this.gate = gate;
        this.complexService = complexService;
        this.cache = cache;
    }

 /** 매물 상세가 부른다. */
    public ReferenceCardResponse getReferences(Long propertyId, String legalDongCode, String dealMonth) {
        final Property property = propertyAccessGuard.require(propertyId);
        if (dealMonth != null && !dealMonth.isBlank()) {
            return collect(property, legalDongCode, dealMonth);
        }
        final Complex complex = complexService.of(property);
        final List<ReferenceTransaction> stored = storedFor(complex, property);
        if (!stored.isEmpty()) {
            return toCard(property, stored, blankToNull(legalDongCode) != null
                    ? legalDongCode
                    : legalDongCodeService.deriveSigunguCode(property.addressJibun()).orElse(null));
        }
        final String miss = lookupKey(complex, property);
        if (cache.get(CachePort.REFERENCE_MISS, miss).isPresent()) {
            return collect(property, legalDongCode, null);
        }

        if (cache.get(CachePort.REFERENCE_LOOKING, miss).isEmpty()) {
            cache.put(CachePort.REFERENCE_LOOKING, miss, "1", LOOKING_TTL);
            gate.detach(() -> {
                try {
                    collect(property, legalDongCode, null);
                } catch (RuntimeException e) {
                    log.warn("Background reference fetch failed. propertyId={}, cause={}",
                            propertyId, e.toString());
                } finally {
                    cache.evict(CachePort.REFERENCE_LOOKING, miss);
                }
            });
        }
        return ReferenceCardResponse.looking(property.priceDeposit(), lookbackMonths,
                legalDongCodeService.deriveSigunguCode(property.addressJibun()).orElse(null));
    }

 /** 등록 직후 배경 보정이 부른다. */
    public void prefetch(Long propertyId) {
        propertyRepository.findById(propertyId).ifPresent(property -> {
            final String key = lookupKey(complexService.of(property), property);
            cache.put(CachePort.REFERENCE_LOOKING, key, "1", LOOKING_TTL);
            try {
                collect(property, null, null);
            } finally {
                cache.evict(CachePort.REFERENCE_LOOKING, key);
            }
        });
    }

    private ReferenceCardResponse collect(Property property, String legalDongCode,
                                          String dealMonth) {
        final Long propertyId = property.id();

        final String lawdCd = blankToNull(legalDongCode) != null
                ? legalDongCode
                : legalDongCodeService.deriveSigunguCode(property.addressJibun()).orElse(null);

        final Complex complex = complexService.of(property);
        final List<ReferenceTransaction> cached = storedFor(complex, property);
        if (!cached.isEmpty()) {
            return toCard(property, cached, lawdCd);
        }
        if (dealMonth == null && cache.get(CachePort.REFERENCE_MISS, lookupKey(complex, property)).isPresent()) {
            log.debug("Skipping ministry lookup - nothing matched recently. propertyId={}", propertyId);
            return ReferenceCardResponse.notLookedUp(property.priceDeposit(), lookbackMonths, lawdCd);
        }

        final String month = blankToNull(dealMonth) != null
                ? dealMonth
                : YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        if (lawdCd == null) {
            cache.put(CachePort.REFERENCE_MISS, lookupKey(complex, property), "1", BLOCKED_TTL);
            log.info("Skipping ministry lookup - legal dong code not found. propertyId={}, jibunAddress={}",
                    propertyId, property.addressJibun());
            return ReferenceCardResponse.notLookedUp(property.priceDeposit(), lookbackMonths, lawdCd);
        }

        final List<ReferenceTrade> trades = fetchMonths(lawdCd, month, dealMonth != null);
        final int nameMatched = (int) trades.stream()
                .filter(trade -> ComplexMatch.same(
                        property.addressJibun(), property.name(), trade))
                .count();
        final List<ReferenceTransaction> saved = trades.stream()
                .filter(trade -> matches(property, trade))
                .sorted(Comparator.comparing(ReferenceTrade::contractDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_SAVED)
                .map(trade -> referenceTransactionRepository.save(new ReferenceTransaction(
                        null, complex.id(), ReferenceDealType.TRADE, trade.contractDate(),
                        trade.dealAmount(), trade.areaM2(), trade.floorNo(),
                        ReferenceSource.MINISTRY_TRADE, Instant.now())))
                .toList();
        if (!saved.isEmpty()) {
            return toCard(property, saved, lawdCd, trades.size(), nameMatched, false);
        }

        final List<ReferenceTransaction> otherAreas = trades.stream()
                .filter(trade -> ComplexMatch.same(
                        property.addressJibun(), property.name(), trade))
                .sorted(Comparator.comparing(ReferenceTrade::contractDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_SAVED)
                .map(trade -> new ReferenceTransaction(
                        null, complex.id(), ReferenceDealType.TRADE, trade.contractDate(),
                        trade.dealAmount(), trade.areaM2(), trade.floorNo(),
                        ReferenceSource.MINISTRY_TRADE, Instant.now()))
                .toList();

        cache.put(CachePort.REFERENCE_MISS, lookupKey(complex, property), "1", MISS_TTL);
        logSamples(property, trades);
        log.info("No reference trades matched. propertyId={}, name={}, areaM2={}, fetched={}, "
                        + "nameMatched={}, otherAreas={}",
                propertyId, property.name(), property.areaExclusiveM2(), trades.size(),
                nameMatched, otherAreas.size());
        return toCard(property, otherAreas, lawdCd, trades.size(), nameMatched, !otherAreas.isEmpty());
    }

 /** 여러 달을 훑어 거래를 모은다. */
    private List<ReferenceTrade> fetchMonths(String lawdCd, String baseMonth, boolean exactMonth) {
        if (exactMonth) {
            return orEmpty(ministryReferencePort.fetchTrades(lawdCd, baseMonth));
        }
        final YearMonth start = YearMonth.parse(baseMonth, DateTimeFormatter.ofPattern("yyyyMM"))
                .minusMonths(REPORTING_LAG_MONTHS);
        final List<ReferenceTrade> all = new ArrayList<>();
        for (int i = 0; i < lookbackMonths; i++) {
            all.addAll(orEmpty(ministryReferencePort.fetchTrades(
                    lawdCd, start.minusMonths(i).format(DateTimeFormatter.ofPattern("yyyyMM")))));
        }
        log.info("Ministry trades collected. lawdCd={}, months={}, from={}, trades={}",
                lawdCd, lookbackMonths, start.minusMonths(lookbackMonths - 1L), all.size());
        return all;
    }

    private List<ReferenceTrade> orEmpty(List<ReferenceTrade> trades) {
        return trades == null ? List.of() : trades;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

 /** 이 매물이 볼 실거래. 단지가 같고 평형이 비슷하면 같은 자료를 본다. */
    private List<ReferenceTransaction> storedFor(Complex complex, Property property) {
        return referenceTransactionRepository.findByComplexAndArea(
                complex.id(), property.areaExclusiveM2(), AREA_TOLERANCE);
    }

 /** 캐시의 열쇠. 단지와 평형. 알아낸 것은 단지의 성질이지 매물의 성질이 아니다. */
    private static String lookupKey(Complex complex, Property property) {
        final BigDecimal area = property.areaExclusiveM2();
        return complex.id() + "@" + (area == null
                ? "?"
                : area.setScale(0, RoundingMode.HALF_UP).toPlainString());
    }

 /** 참고 대상 판정. 같은 단지의 같은 면적대여야 한다. 이름을 확인할 수 없을 때만 면적으로 폴백한다. */
    private boolean matches(Property property, ReferenceTrade trade) {
        final boolean nameKnown = ComplexMatch.same(
                property.addressJibun(), property.name(), trade);
        final boolean areaKnown = property.areaExclusiveM2() != null && trade.areaM2() != null
                && property.areaExclusiveM2().signum() > 0;

        if (!nameKnown) {
            return false;
        }
        if (!areaKnown) {
            return nameKnown;
        }
        final double diff = Math.abs(property.areaExclusiveM2().doubleValue() - trade.areaM2().doubleValue());
        return diff / property.areaExclusiveM2().doubleValue() <= AREA_TOLERANCE;
    }

 /** 왜 하나도 안 맞았는지 실물을 보여 준다. */
    private void logSamples(Property property, List<ReferenceTrade> trades) {
        final Optional<JibunAddress> mine = JibunAddress.of(property.addressJibun());
        final List<ReferenceTrade> sameDong = trades.stream()
                .filter(t -> mine.isPresent() && t.lot().map(mine.get()::sameDong).orElse(false))
                .toList();
        final List<ReferenceTrade> samples = sameDong.isEmpty() ? trades : sameDong;
        log.info("Reference match diagnostics. propertyId={}, myAddress={}, myLot={}, "
                        + "sameDongCount={}, samples=[{}]",
                property.id(), property.addressJibun(), mine.orElse(null), sameDong.size(),
                samples.stream().limit(5)
                        .map(t -> String.format("%s|%s %s|%s", t.apartmentName(),
                                t.legalDong(), t.jibun(), t.areaM2()))
                        .collect(Collectors.joining(" · ")));
    }

    private ReferenceCardResponse toCard(Property property, List<ReferenceTransaction> transactions,
                                         String lawdCd) {
        return toCard(property, transactions, lawdCd, transactions.size(), transactions.size(), false);
    }

    private ReferenceCardResponse toCard(Property property, List<ReferenceTransaction> transactions,
                                         String lawdCd, int fetched, int nameMatched,
                                         boolean areaMismatch) {
        final List<ReferenceTransactionResponse> list = transactions.stream()
                .sorted(Comparator.comparing(ReferenceTransaction::contractDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(t -> new ReferenceTransactionResponse(t.contractDate(), t.price(), t.floorNo(), t.areaM2()))
                .toList();
        final Long asking = property.priceDeposit();
        if (isComputeGapRate(asking, list)) {
            return new ReferenceCardResponse(list, asking, null, null, lookbackMonths, lawdCd,
                    fetched, nameMatched, areaMismatch, false);
        }
        final long latest = list.getFirst().price();
        final BigDecimal gap = BigDecimal.valueOf((asking - latest) * 100.0 / latest).setScale(1, RoundingMode.HALF_UP);
        return new ReferenceCardResponse(list, asking, areaMismatch ? null : gap, null,
                lookbackMonths, lawdCd, fetched, nameMatched, areaMismatch, false);
    }

    private static boolean isComputeGapRate(Long asking, List<ReferenceTransactionResponse> list) {
        return asking == null || list.isEmpty() || list.getFirst().price() == null || list.getFirst().price() <= 0;
    }
}
