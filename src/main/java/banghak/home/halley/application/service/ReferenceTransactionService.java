package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.ReferenceCardResponse;
import banghak.home.halley.adapter.inbound.web.dto.ReferenceTransactionResponse;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.ReferenceTransactionRepository;
import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.application.port.out.external.MinistryReferencePort;
import banghak.home.halley.config.exception.NotFoundListingsException;
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
    /**
     * 이번 달은 건너뛴다.
     *
     * <p>계약 후 30일 이내 신고이고 공개는 그 뒤라, <b>이번 달 계약은 대부분 아직
     * 올라오지 않았습니다.</b> 불러 봐야 빈 응답만 받습니다.
     */
    private static final int REPORTING_LAG_MONTHS = 1;
    /** 저장할 최대 건수. 담보가치는 중앙값을 쓰므로 이만큼이면 넉넉하다. */
    private static final int MAX_SAVED = 50;

    private static final double AREA_TOLERANCE = 0.15;
    /** 이보다 짧은 단지명은 우연히 걸린다 — 판정에 쓰지 않는다. */
    private static final int MIN_NAME_LENGTH = 2;

    private final PropertyRepository propertyRepository;
    private final PropertyAccessGuard propertyAccessGuard;
    private final ReferenceTransactionRepository referenceTransactionRepository;
    private final MinistryReferencePort ministryReferencePort;
    private final LegalDongCodeService legalDongCodeService;
    /**
     * 거슬러 볼 개월 수 (설계 I98).
     *
     * <p>국토부 API는 <b>한 번에 한 달치</b>만 줍니다. 예전에는 이번 달만 불러 참고 거래가
     * 거의 늘 비어 있었습니다 — 한 단지의 한 달 거래는 원래 0건이 흔합니다.
     */
    private final int lookbackMonths;
    /** 배경 조회를 다른 보정과 같은 줄에 세운다 (설계 I108). */
    private final banghak.home.halley.config.VirtualThreadGate gate;
    private final CachePort cache;

    /**
     * 헛걸음을 기억해 두는 시간 (설계 I219).
     *
     * <p>국토부 자료는 <b>달 단위로 들어옵니다.</b> 오늘 없던 거래가 오늘 오후에
     * 생기지는 않습니다 — 하루면 충분하고, 새 달이 오면 어차피 만료됩니다.
     */
    private static final Duration MISS_TTL = Duration.ofHours(24);

    /**
     * 배경 조회가 <b>돌고 있다는 표시</b>의 수명 (설계 I262).
     *
     * <p>12개월치를 초당 4건 제한(I140) 아래서 훑으므로 넉넉해야 하지만,
     * <b>너무 길면 죽은 표시가 남아</b> 아무도 다시 못 띄웁니다. 3분이면
     * 12번 호출에 충분하고, 잘못돼도 3분 뒤에 풀립니다.
     */
    private static final Duration LOOKING_TTL = Duration.ofMinutes(3);

    /**
     * <b>못 찾은 게 아니라 못 찾아본</b> 경우의 수명 (설계 I262).
     *
     * <p>법정동코드 사전이 아직 안 채워졌으면 자료가 없는 것이 아니라 <b>물어볼 주소를
     * 못 만든 것</b>입니다. 사전이 채워지면 곧 풀려야 하므로 하루는 너무 깁니다.
     */
    private static final Duration BLOCKED_TTL = Duration.ofMinutes(10);

    public ReferenceTransactionService(PropertyAccessGuard propertyAccessGuard,
                                  PropertyRepository propertyRepository,
                                       ReferenceTransactionRepository referenceTransactionRepository,
                                       MinistryReferencePort ministryReferencePort,
                                       LegalDongCodeService legalDongCodeService,
                                       @Value("${ministry.reference.lookback-months:12}")
                                       int lookbackMonths,
                                       banghak.home.halley.config.VirtualThreadGate gate,
                                       CachePort cache) {
        this.propertyAccessGuard = propertyAccessGuard;
        this.propertyRepository = propertyRepository;
        this.referenceTransactionRepository = referenceTransactionRepository;
        this.ministryReferencePort = ministryReferencePort;
        this.legalDongCodeService = legalDongCodeService;
        this.lookbackMonths = lookbackMonths;
        this.gate = gate;
        this.cache = cache;
    }

    /**
     * 매물 상세가 부른다 (설계 I184).
     *
     * <p><b>여기서 국토부를 부르지 않습니다.</b> 저장된 것이 없으면 12개월을 훑는데,
     * 초당 제한(I140)까지 걸려 <b>3초 넘게 화면이 멈춥니다.</b> 그동안 상세 모달이
     * 통째로 기다립니다 — 중개사·토지이용계획은 캐시에서 곧바로 오는데도 그렇습니다.
     *
     * <p>저장된 것만 돌려주고, 없으면 <b>배경에서 받아 둡니다.</b> 사용자가 달을 지정해
     * 물었을 때(`dealMonth`)만 기다렸다 답합니다 — 그건 명시적으로 시킨 일입니다.
     */
    public ReferenceCardResponse getReferences(Long propertyId, String legalDongCode, String dealMonth) {
        final Property property = propertyAccessGuard.require(propertyId);
        if (dealMonth != null && !dealMonth.isBlank()) {
            return collect(property, legalDongCode, dealMonth);
        }
        final List<ReferenceTransaction> stored = referenceTransactionRepository.findByPropertyId(propertyId);
        if (!stored.isEmpty()) {
            // 저장된 것을 돌려줄 때도 무엇으로 물었는지 함께 말한다 (설계 I227)
            return toCard(property, stored, blankToNull(legalDongCode) != null
                    ? legalDongCode
                    : legalDongCodeService.deriveSigunguCode(property.addressJibun()).orElse(null));
        }
        // <b>이미 찾아봤고 없었다면 기다리게 하면 안 된다 (설계 I262).</b>
        // [I259]는 "저장된 게 없다"만 보고 <b>늘</b> 받아 오는 중이라고 답했습니다.
        // 못 찾은 매물은 저장될 것이 영영 없으니 <b>프로그래스바가 영원히 돕니다</b> —
        // 실제로 한 시간을 돌았습니다. 헛걸음 표시가 그 사실을 이미 알고 있었는데
        // 이 길만 그것을 안 봤습니다. 아래 호출은 국토부를 부르지 않고 곧바로 답합니다
        final String miss = String.valueOf(propertyId);
        if (cache.get(CachePort.REFERENCE_MISS, miss).isPresent()) {
            return collect(property, legalDongCode, null);
        }

        // 화면은 기다리지 않는다. <b>받아 오는 중이라고 말하고</b> 화면이 다시 묻는다 (설계 I259).
        // 다만 <b>이미 도는 것이 있으면 또 띄우지 않는다</b> (설계 I262) —
        // 3초마다 묻는 화면 하나가 1분에 스무 벌을 띄우고 있었습니다
        if (cache.get(CachePort.REFERENCE_LOOKING, miss).isEmpty()) {
            cache.put(CachePort.REFERENCE_LOOKING, miss, "1", LOOKING_TTL);
            // <b>맡기고 곧바로 답한다 (설계 I262).</b> 전에는 runAll 을 불러
            // 12개월치가 다 끝날 때까지 이 요청이 붙잡혀 있었습니다
            gate.detach(() -> {
                try {
                    collect(property, legalDongCode, null);
                } catch (RuntimeException e) {
                    log.warn("Background reference fetch failed. propertyId={}, cause={}",
                            propertyId, e.toString());
                } finally {
                    // 끝났으면 <b>반드시</b> 지운다 — 남으면 다음 사람이 영영 못 띄운다
                    cache.evict(CachePort.REFERENCE_LOOKING, miss);
                }
            });
        }
        return ReferenceCardResponse.looking(property.priceDeposit(), lookbackMonths,
                legalDongCodeService.deriveSigunguCode(property.addressJibun()).orElse(null));
    }

    /**
     * 등록 직후 배경 보정이 부른다 (설계 I106).
     *
     * <p><b>격리 길목을 타지 않습니다.</b> 배경 스레드에는 로그인 사용자가 없어
     * 길목이 전부 막습니다 — 그래서 실거래가가 등록 시 한 번도 채워지지 않았습니다.
     * 이미 인가된 매물 번호로 도는 것이라 다시 확인할 대상이 아닙니다.
     */
    public void prefetch(Long propertyId) {
        propertyRepository.findById(propertyId).ifPresent(property -> {
            // 등록 직후 상세를 열면 <b>같은 조회가 두 벌</b> 돕니다 (설계 I262).
            // 여기도 같은 표시를 세워 화면 쪽이 기다리게 합니다
            final String key = String.valueOf(propertyId);
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

        // 무엇으로 물었는지는 <b>결과가 있든 없든</b> 알려 준다 (설계 I227) —
        // "왜 비었는지"를 확인하려면 코드가 먼저 보여야 한다
        final String lawdCd = blankToNull(legalDongCode) != null
                ? legalDongCode
                : legalDongCodeService.deriveSigunguCode(property.addressJibun()).orElse(null);

        final List<ReferenceTransaction> cached = referenceTransactionRepository.findByPropertyId(propertyId);
        if (!cached.isEmpty()) {
            return toCard(property, cached, lawdCd);
        }
        // <b>못 찾은 것도 결과입니다 (설계 I219).</b> 저장할 거래가 없다고 아무것도
        // 남기지 않으면, 상세를 열 때마다 12개월치를 다시 받아 옵니다 —
        // 실제로 그러고 있었습니다. 사용자가 특정 달을 물을 때는 무시합니다
        if (dealMonth == null && cache.get(CachePort.REFERENCE_MISS, String.valueOf(propertyId)).isPresent()) {
            log.debug("Skipping ministry lookup - nothing matched recently. propertyId={}", propertyId);
            return ReferenceCardResponse.notLookedUp(property.priceDeposit(), lookbackMonths, lawdCd);
        }

        // 계약년월이 없으면 현재 월 사용
        final String month = blankToNull(dealMonth) != null
                ? dealMonth
                : YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        if (lawdCd == null) {
            // <b>여기도 끝난 것이다 (설계 I262).</b> 예전에는 아무 자국을 안 남기고
            // 돌아섰습니다 — 화면은 "받아 오는 중"인 채로 <b>영영 멈추지 않았습니다.</b>
            // 다만 이건 자료가 없는 게 아니라 <b>사전이 아직 없는 것</b>이라 짧게 기억합니다
            cache.put(CachePort.REFERENCE_MISS, String.valueOf(propertyId), "1", BLOCKED_TTL);
            log.info("Skipping ministry lookup - legal dong code not found. propertyId={}, jibunAddress={}",
                    propertyId, property.addressJibun());
            return ReferenceCardResponse.notLookedUp(property.priceDeposit(), lookbackMonths, lawdCd);
        }

        final List<ReferenceTrade> trades = fetchMonths(lawdCd, month, dealMonth != null);
        // 비었을 때 <b>어느 단계에서 걸렸는지</b> 말해 주려고 센다 (설계 I232)
        // 이름이 아니라 <b>단지가</b> 맞는 수다 (설계 I257) — 주소로 잡힌 것도 센다
        final int nameMatched = (int) trades.stream()
                .filter(trade -> ComplexMatch.same(
                        property.addressJibun(), property.name(), trade))
                .count();
        final List<ReferenceTransaction> saved = trades.stream()
                .filter(trade -> matches(property, trade))
                .sorted(Comparator.comparing(ReferenceTrade::contractDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_SAVED)
                .map(trade -> referenceTransactionRepository.save(new ReferenceTransaction(
                        null, propertyId, ReferenceDealType.TRADE, trade.contractDate(),
                        trade.dealAmount(), trade.areaM2(), trade.floorNo(),
                        ReferenceSource.MINISTRY_TRADE, Instant.now())))
                .toList();
        if (!saved.isEmpty()) {
            return toCard(property, saved, lawdCd, trades.size(), nameMatched, false);
        }

        // 이름은 맞는데 <b>면적이 하나도 안 맞는</b> 경우 (설계 I232).
        // 조용히 "없습니다" 하면 <b>단지가 실제로 거래되고 있다는 사실</b>이 가려집니다 —
        // 상계주공7단지가 그랬습니다: 매물 전용면적에 <b>공급면적(71.02)</b>이 들어가
        // 있었는데, 화면이 빈 채로만 있어 아무도 못 알아챘습니다.
        // <b>저장하지는 않습니다</b> — 다른 평형이라 이 매물의 참고 시세가 아닙니다
        final List<ReferenceTransaction> otherAreas = trades.stream()
                .filter(trade -> ComplexMatch.same(
                        property.addressJibun(), property.name(), trade))
                .sorted(Comparator.comparing(ReferenceTrade::contractDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_SAVED)
                .map(trade -> new ReferenceTransaction(
                        null, propertyId, ReferenceDealType.TRADE, trade.contractDate(),
                        trade.dealAmount(), trade.areaM2(), trade.floorNo(),
                        ReferenceSource.MINISTRY_TRADE, Instant.now()))
                .toList();

        // 헛걸음을 기억한다 (설계 I219) — 다음 상세에서 12개월치를 또 받지 않는다
        cache.put(CachePort.REFERENCE_MISS, String.valueOf(propertyId), "1", MISS_TTL);
        // <b>무엇과 무엇을 비교했는지</b> 남긴다 (설계 I260).
        // "0건 맞음"만으로는 이름이 다른 건지, 동·번지가 안 온 건지, 우리 주소를
        // 못 읽은 건지 알 수 없다 — 실제로 그것 때문에 원인을 못 짚었다
        logSamples(property, trades);
        log.info("No reference trades matched. propertyId={}, name={}, areaM2={}, fetched={}, "
                        + "nameMatched={}, otherAreas={}",
                propertyId, property.name(), property.areaExclusiveM2(), trades.size(),
                nameMatched, otherAreas.size());
        return toCard(property, otherAreas, lawdCd, trades.size(), nameMatched, !otherAreas.isEmpty());
    }

    /**
     * 여러 달을 훑어 거래를 모은다 (설계 I98).
     *
     * <p>국토부 API가 한 달치만 주므로 <b>달마다 한 번씩</b> 부릅니다. 매물당 한 번만 돌고
     * 결과는 저장되므로(캐시) 등록 시점의 비용입니다.
     *
     * @param exactMonth 호출자가 달을 지정했으면 그 달만 본다 — 화면에서 특정 월을 물을 때다
     */
    private List<ReferenceTrade> fetchMonths(String lawdCd, String baseMonth, boolean exactMonth) {
        if (exactMonth) {
            // null = 조회 실패 (설계 I140). 화면은 '없음'과 구분하지 않으므로 빈 목록으로 준다
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

    /**
     * 참고 대상 판정 — <b>같은 단지의 같은 면적대</b>여야 한다 (설계 I71).
     *
     * <p>예전에는 면적만 맞으면 받아들였습니다. 그러면 <b>같은 법정동의 다른 단지</b> 거래가
     * 통째로 섞입니다. 실측(대치동 84㎡)에서 21억·20.5억과 함께 9.85억·13억이 들어왔습니다 —
     * 이 값들로 담보가치를 매기면 크게 틀어집니다.
     *
     * <p>단지명은 표기가 흔들립니다(`은마` / `은마아파트` / `은마아파트(테스트)`). 괄호·`아파트`·
     * 공백을 걷어내고 <b>한쪽이 다른 쪽을 품는지</b>로 봅니다. 두 글자 미만은 우연히 걸리므로 뺍니다.
     *
     * <p>단지명을 확인할 수 없을 때만 면적으로 폴백합니다. <b>이름이 다르면 제외</b>합니다 —
     * 참고 카드가 비는 것이 남의 단지 가격을 이 매물 것처럼 보여주는 것보다 낫습니다.
     */
    private boolean matches(Property property, ReferenceTrade trade) {
        // 규칙은 `ComplexName` 하나다 (설계 I230) — 전망과 다르게 정규화하다 갈라졌다
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

    /**
     * 왜 하나도 안 맞았는지 <b>실물을 보여 준다</b> (설계 I260).
     *
     * <p>수만 세는 로그로는 원인을 못 짚습니다. 우리가 읽은 주소와, 국토부가 준
     * 거래 몇 건의 <b>이름·동·번지</b>를 같이 남깁니다 — 같은 동의 것을 먼저 보여
     * 줍니다. 동이 다른 것은 어차피 남입니다.
     */
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
        // 다른 평형과 견준 괴리는 뜻이 없다 (설계 I232)
        return new ReferenceCardResponse(list, asking, areaMismatch ? null : gap, null,
                lookbackMonths, lawdCd, fetched, nameMatched, areaMismatch, false);
    }

    private static boolean isComputeGapRate(Long asking, List<ReferenceTransactionResponse> list) {
        return asking == null || list.isEmpty() || list.getFirst().price() == null || list.getFirst().price() <= 0;
    }
}
