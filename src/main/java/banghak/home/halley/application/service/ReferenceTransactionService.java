package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.ReferenceCardResponse;
import banghak.home.halley.adapter.inbound.web.dto.ReferenceTransactionResponse;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.ReferenceTransactionRepository;
import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.application.port.out.external.MinistryReferencePort;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.domain.property.ComplexName;
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
        // 화면은 기다리지 않는다. 다음에 열면 채워져 있다
        gate.runAll(List.of(() -> {
            try {
                collect(property, legalDongCode, null);
            } catch (RuntimeException e) {
                log.warn("Background reference fetch failed. propertyId={}, cause={}",
                        propertyId, e.toString());
            }
            return null;
        }));
        return ReferenceCardResponse.notLookedUp(property.priceDeposit(), lookbackMonths, null);
    }

    /**
     * 등록 직후 배경 보정이 부른다 (설계 I106).
     *
     * <p><b>격리 길목을 타지 않습니다.</b> 배경 스레드에는 로그인 사용자가 없어
     * 길목이 전부 막습니다 — 그래서 실거래가가 등록 시 한 번도 채워지지 않았습니다.
     * 이미 인가된 매물 번호로 도는 것이라 다시 확인할 대상이 아닙니다.
     */
    public void prefetch(Long propertyId) {
        propertyRepository.findById(propertyId)
                .ifPresent(property -> collect(property, null, null));
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
            log.info("Skipping ministry lookup - legal dong code not found. propertyId={}, jibunAddress={}",
                    propertyId, property.addressJibun());
            return ReferenceCardResponse.notLookedUp(property.priceDeposit(), lookbackMonths, lawdCd);
        }

        final List<ReferenceTrade> trades = fetchMonths(lawdCd, month, dealMonth != null);
        // 비었을 때 <b>어느 단계에서 걸렸는지</b> 말해 주려고 센다 (설계 I231)
        final int nameMatched = (int) trades.stream()
                .filter(trade -> !ComplexName.comparable(property.name(), trade.apartmentName())
                        || ComplexName.same(property.name(), trade.apartmentName()))
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
        // 비었을 때 왜 비었는지 남긴다 — 받아온 게 없는 것과 걸러진 것은 다른 상황이다
        if (saved.isEmpty()) {
            // 헛걸음을 기억한다 (설계 I219) — 다음 상세에서 12개월치를 또 받지 않는다
            cache.put(CachePort.REFERENCE_MISS, String.valueOf(propertyId), "1", MISS_TTL);
            log.info("No reference trades matched. propertyId={}, name={}, areaM2={}, fetched={}, nameMatched={}",
                    propertyId, property.name(), property.areaExclusiveM2(), trades.size(), nameMatched);
        }
        return toCard(property, saved, lawdCd, trades.size(), nameMatched);
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
        final boolean nameKnown = ComplexName.comparable(property.name(), trade.apartmentName());
        final boolean areaKnown = property.areaExclusiveM2() != null && trade.areaM2() != null
                && property.areaExclusiveM2().signum() > 0;

        if (nameKnown && !ComplexName.same(property.name(), trade.apartmentName())) {
            return false;
        }
        if (!areaKnown) {
            return nameKnown;
        }
        final double diff = Math.abs(property.areaExclusiveM2().doubleValue() - trade.areaM2().doubleValue());
        return diff / property.areaExclusiveM2().doubleValue() <= AREA_TOLERANCE;
    }

    /** `은마아파트(테스트)` → `은마`. 표기 흔들림을 걷어낸다. */
    private String normalizeComplexName(String name) {
        if (name == null) {
            return null;
        }
        final String normalized = name
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("아파트|APT|apt", "")
                .replaceAll("\\s+", "")
                .toLowerCase(java.util.Locale.ROOT);
        return normalized.length() < MIN_NAME_LENGTH ? null : normalized;
    }

    private ReferenceCardResponse toCard(Property property, List<ReferenceTransaction> transactions,
                                         String lawdCd) {
        return toCard(property, transactions, lawdCd, transactions.size(), transactions.size());
    }

    private ReferenceCardResponse toCard(Property property, List<ReferenceTransaction> transactions,
                                         String lawdCd, int fetched, int nameMatched) {
        final List<ReferenceTransactionResponse> list = transactions.stream()
                .sorted(Comparator.comparing(ReferenceTransaction::contractDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(t -> new ReferenceTransactionResponse(t.contractDate(), t.price(), t.floorNo()))
                .toList();
        final Long asking = property.priceDeposit();
        if (isComputeGapRate(asking, list)) {
            return new ReferenceCardResponse(list, asking, null, null, lookbackMonths, lawdCd, fetched, nameMatched);
        }
        final long latest = list.getFirst().price();
        final BigDecimal gap = BigDecimal.valueOf((asking - latest) * 100.0 / latest).setScale(1, RoundingMode.HALF_UP);
        return new ReferenceCardResponse(list, asking, gap, null, lookbackMonths, lawdCd, fetched, nameMatched);
    }

    private static boolean isComputeGapRate(Long asking, List<ReferenceTransactionResponse> list) {
        return asking == null || list.isEmpty() || list.getFirst().price() == null || list.getFirst().price() <= 0;
    }
}
