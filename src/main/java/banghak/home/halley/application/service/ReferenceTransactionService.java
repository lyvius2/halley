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

    /** 같은 타입으로 볼 전용면적 오차.  */
    private static final int REPORTING_LAG_MONTHS = 1;
    /** 저장할 최대 건수. 담보가치는 중앙값을 쓰므로 이만큼이면 넉넉하다.  */
    private static final int MAX_SAVED = 50;

    private static final double AREA_TOLERANCE = 0.15;
    /** 이보다 짧은 단지명은 우연히 걸린다 — 판정에 쓰지 않는다.  */
    private static final int MIN_NAME_LENGTH = 2;

    private final PropertyRepository propertyRepository;
    private final PropertyAccessGuard propertyAccessGuard;
    private final ReferenceTransactionRepository referenceTransactionRepository;
    private final MinistryReferencePort ministryReferencePort;
    private final LegalDongCodeService legalDongCodeService;
    private final int lookbackMonths;
    /** 배경 조회를 다른 보정과 같은 줄에 세운다.  */
    private final VirtualThreadGate gate;
    private final CachePort cache;
    /** 실거래는 매물이 아니라 단지와 평형에 붙는다.  */
    private final ComplexService complexService;

    private static final Duration MISS_TTL = Duration.ofHours(24);

    private static final Duration LOOKING_TTL = Duration.ofMinutes(3);

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

    public ReferenceCardResponse getReferences(Long propertyId, String legalDongCode, String dealMonth) {
        final Property property = propertyAccessGuard.require(propertyId);
        if (dealMonth != null && !dealMonth.isBlank()) {
            return collect(property, legalDongCode, dealMonth);
        }
        final Complex complex = complexService.of(property);
        final List<ReferenceTransaction> stored = storedFor(complex, property);
        if (!stored.isEmpty()) {
            // 저장된 것을 돌려줄 때도 무엇으로 물었는지 함께 말한다
            return toCard(property, stored, blankToNull(legalDongCode) != null
                    ? legalDongCode
                    : legalDongCodeService.deriveSigunguCode(property.addressJibun()).orElse(null));
        }
        // 이미 조회했지만 거래가 없었던 매물은 즉시 결과를 반환한다.
        // 조회 실패를 다시 조회 중으로 표시하면 화면이 끝없이 대기한다.
        final String miss = lookupKey(complex, property);
        if (cache.get(CachePort.REFERENCE_MISS, miss).isPresent()) {
            return collect(property, legalDongCode, null);
        }

        // 화면은 기다리지 않는다. 받아 오는 중이라고 말하고 화면이 다시 묻는다.
        // 다만 이미 도는 것이 있으면 또 띄우지 않는다 —
        // 3초마다 묻는 화면 하나가 1분에 스무 벌을 띄우고 있었습니다
        if (cache.get(CachePort.REFERENCE_LOOKING, miss).isEmpty()) {
            cache.put(CachePort.REFERENCE_LOOKING, miss, "1", LOOKING_TTL);
            // 맡기고 곧바로 답한다. 전에는 runAll 을 불러
            // 12개월치가 다 끝날 때까지 이 요청이 붙잡혀 있었습니다
            gate.detach(() -> {
                try {
                    collect(property, legalDongCode, null);
                } catch (RuntimeException e) {
                    log.warn("Background reference fetch failed. propertyId={}, cause={}",
                            propertyId, e.toString());
                } finally {
                    // 끝났으면 반드시 지운다 — 남으면 다음 사람이 영영 못 띄운다
                    cache.evict(CachePort.REFERENCE_LOOKING, miss);
                }
            });
        }
        return ReferenceCardResponse.looking(property.priceDeposit(), lookbackMonths,
                legalDongCodeService.deriveSigunguCode(property.addressJibun()).orElse(null));
    }

    public void prefetch(Long propertyId) {
        propertyRepository.findById(propertyId).ifPresent(property -> {
            // 등록 직후 상세를 열면 같은 조회가 두 벌 돕니다.
            // 여기도 같은 표시를 세워 화면 쪽이 기다리게 합니다
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

        // 무엇으로 물었는지는 결과가 있든 없든 알려 준다 —
        // "왜 비었는지"를 확인하려면 코드가 먼저 보여야 한다
        final String lawdCd = blankToNull(legalDongCode) != null
                ? legalDongCode
                : legalDongCodeService.deriveSigunguCode(property.addressJibun()).orElse(null);

        final Complex complex = complexService.of(property);
        final List<ReferenceTransaction> cached = storedFor(complex, property);
        if (!cached.isEmpty()) {
            return toCard(property, cached, lawdCd);
        }
        // 못 찾은 것도 결과입니다. 저장할 거래가 없다고 아무것도
        // 남기지 않으면, 상세를 열 때마다 12개월치를 다시 받아 옵니다 —
        // 실제로 그러고 있었습니다. 사용자가 특정 달을 물을 때는 무시합니다
        if (dealMonth == null && cache.get(CachePort.REFERENCE_MISS, lookupKey(complex, property)).isPresent()) {
            log.debug("Skipping ministry lookup - nothing matched recently. propertyId={}", propertyId);
            return ReferenceCardResponse.notLookedUp(property.priceDeposit(), lookbackMonths, lawdCd);
        }

        // 계약년월이 없으면 현재 월 사용
        final String month = blankToNull(dealMonth) != null
                ? dealMonth
                : YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        if (lawdCd == null) {
            // 여기도 끝난 것이다. 예전에는 아무 자국을 안 남기고
            // 돌아섰습니다 — 화면은 "받아 오는 중"인 채로 영영 멈추지 않았습니다.
            // 다만 이건 자료가 없는 게 아니라 사전이 아직 없는 것이라 짧게 기억합니다
            cache.put(CachePort.REFERENCE_MISS, lookupKey(complex, property), "1", BLOCKED_TTL);
            log.info("Skipping ministry lookup - legal dong code not found. propertyId={}, jibunAddress={}",
                    propertyId, property.addressJibun());
            return ReferenceCardResponse.notLookedUp(property.priceDeposit(), lookbackMonths, lawdCd);
        }

        final List<ReferenceTrade> trades = fetchMonths(lawdCd, month, dealMonth != null);
        // 비었을 때 어느 단계에서 걸렸는지 말해 주려고 센다
        // 이름이 아니라 단지가 맞는 수다 — 주소로 잡힌 것도 센다
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

        // 이름은 맞는데 면적이 하나도 안 맞는 경우.
        // 조용히 "없습니다" 하면 단지가 실제로 거래되고 있다는 사실이 가려집니다 —
        // 상계주공7단지가 그랬습니다: 매물 전용면적에 공급면적(71.02)이 들어가
        // 있었는데, 화면이 빈 채로만 있어 아무도 못 알아챘습니다.
        // 저장하지는 않습니다 — 다른 평형이라 이 매물의 참고 시세가 아닙니다
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

        // 헛걸음을 기억한다 — 다음 상세에서 12개월치를 또 받지 않는다
        cache.put(CachePort.REFERENCE_MISS, lookupKey(complex, property), "1", MISS_TTL);
        // 무엇과 무엇을 비교했는지 남긴다.
        // "0건 맞음"만으로는 이름이 다른 건지, 동·번지가 안 온 건지, 우리 주소를
        // 못 읽은 건지 알 수 없다 — 실제로 그것 때문에 원인을 못 짚었다
        logSamples(property, trades);
        log.info("No reference trades matched. propertyId={}, name={}, areaM2={}, fetched={}, "
                        + "nameMatched={}, otherAreas={}",
                propertyId, property.name(), property.areaExclusiveM2(), trades.size(),
                nameMatched, otherAreas.size());
        return toCard(property, otherAreas, lawdCd, trades.size(), nameMatched, !otherAreas.isEmpty());
    }

    private List<ReferenceTrade> fetchMonths(String lawdCd, String baseMonth, boolean exactMonth) {
        if (exactMonth) {
            // null = 조회 실패. 화면은 '없음'과 구분하지 않으므로 빈 목록으로 준다
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

    /** 이 매물이 볼 실거래 — 단지가 같고 평형이 비슷하면 같은 자료를 본다.  */
    private List<ReferenceTransaction> storedFor(Complex complex, Property property) {
        return referenceTransactionRepository.findByComplexAndArea(
                complex.id(), property.areaExclusiveM2(), AREA_TOLERANCE);
    }

    /** 캐시의 열쇠 — 단지와 평형. 알아낸 것은 단지의 성질이지 매물의 성질이 아니다.  */
    private static String lookupKey(Complex complex, Property property) {
        final BigDecimal area = property.areaExclusiveM2();
        return complex.id() + "@" + (area == null
                ? "?"
                : area.setScale(0, RoundingMode.HALF_UP).toPlainString());
    }

    /** 참고 대상 판정 — 같은 단지의 같은 면적대여야 한다. 이름을 확인할 수 없을 때만 면적으로 폴백한다.  */
    private boolean matches(Property property, ReferenceTrade trade) {
        // 규칙은 `ComplexName` 하나다 — 전망과 다르게 정규화하다 갈라졌다
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
        // 다른 평형과 견준 괴리는 뜻이 없다
        return new ReferenceCardResponse(list, asking, areaMismatch ? null : gap, null,
                lookbackMonths, lawdCd, fetched, nameMatched, areaMismatch, false);
    }

    private static boolean isComputeGapRate(Long asking, List<ReferenceTransactionResponse> list) {
        return asking == null || list.isEmpty() || list.getFirst().price() == null || list.getFirst().price() <= 0;
    }
}
