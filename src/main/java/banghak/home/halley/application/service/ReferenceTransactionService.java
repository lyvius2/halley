package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.ReferenceCardResponse;
import banghak.home.halley.adapter.inbound.web.dto.ReferenceTransactionResponse;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.ReferenceTransactionRepository;
import banghak.home.halley.application.port.out.external.MinistryReferencePort;
import banghak.home.halley.config.exception.NotFoundListingsException;
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

    public ReferenceTransactionService(PropertyAccessGuard propertyAccessGuard,
                                  PropertyRepository propertyRepository,
                                       ReferenceTransactionRepository referenceTransactionRepository,
                                       MinistryReferencePort ministryReferencePort,
                                       LegalDongCodeService legalDongCodeService,
                                       @Value("${ministry.reference.lookback-months:24}")
                                       int lookbackMonths) {
        this.propertyAccessGuard = propertyAccessGuard;
        this.propertyRepository = propertyRepository;
        this.referenceTransactionRepository = referenceTransactionRepository;
        this.ministryReferencePort = ministryReferencePort;
        this.legalDongCodeService = legalDongCodeService;
        this.lookbackMonths = lookbackMonths;
    }

    public ReferenceCardResponse getReferences(Long propertyId, String legalDongCode, String dealMonth) {
        final Property property = propertyAccessGuard.require(propertyId);
        return collect(property, legalDongCode, dealMonth);
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

        final List<ReferenceTransaction> cached = referenceTransactionRepository.findByPropertyId(propertyId);
        if (!cached.isEmpty()) {
            return toCard(property, cached);
        }

        // 법정동코드가 없으면 지번주소에서 역매핑, 계약년월이 없으면 현재 월 사용
        final String lawdCd = blankToNull(legalDongCode) != null
                ? legalDongCode
                : legalDongCodeService.deriveSigunguCode(property.addressJibun()).orElse(null);
        final String month = blankToNull(dealMonth) != null
                ? dealMonth
                : YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        if (lawdCd == null) {
            log.info("Skipping ministry lookup - legal dong code not found. propertyId={}, jibunAddress={}",
                    propertyId, property.addressJibun());
            return new ReferenceCardResponse(List.of(), property.priceDeposit(), null, null, lookbackMonths);
        }

        final List<ReferenceTrade> trades = fetchMonths(lawdCd, month, dealMonth != null);
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
            log.info("No reference trades matched. propertyId={}, name={}, areaM2={}, fetched={}",
                    propertyId, property.name(), property.areaExclusiveM2(), trades.size());
        }
        return toCard(property, saved);
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
            return ministryReferencePort.fetchTrades(lawdCd, baseMonth);
        }
        final YearMonth start = YearMonth.parse(baseMonth, DateTimeFormatter.ofPattern("yyyyMM"))
                .minusMonths(REPORTING_LAG_MONTHS);
        final List<ReferenceTrade> all = new ArrayList<>();
        for (int i = 0; i < lookbackMonths; i++) {
            all.addAll(ministryReferencePort.fetchTrades(
                    lawdCd, start.minusMonths(i).format(DateTimeFormatter.ofPattern("yyyyMM"))));
        }
        log.info("Ministry trades collected. lawdCd={}, months={}, from={}, trades={}",
                lawdCd, lookbackMonths, start.minusMonths(lookbackMonths - 1L), all.size());
        return all;
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
        final String propertyName = normalizeComplexName(property.name());
        final String tradeName = normalizeComplexName(trade.apartmentName());
        final boolean nameKnown = propertyName != null && tradeName != null;
        final boolean areaKnown = property.areaExclusiveM2() != null && trade.areaM2() != null
                && property.areaExclusiveM2().signum() > 0;

        if (nameKnown && !sameComplex(propertyName, tradeName)) {
            return false;
        }
        if (!areaKnown) {
            return nameKnown;
        }
        final double diff = Math.abs(property.areaExclusiveM2().doubleValue() - trade.areaM2().doubleValue());
        return diff / property.areaExclusiveM2().doubleValue() <= AREA_TOLERANCE;
    }

    private boolean sameComplex(String left, String right) {
        return left.contains(right) || right.contains(left);
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

    private ReferenceCardResponse toCard(Property property, List<ReferenceTransaction> transactions) {
        final List<ReferenceTransactionResponse> list = transactions.stream()
                .sorted(Comparator.comparing(ReferenceTransaction::contractDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(t -> new ReferenceTransactionResponse(t.contractDate(), t.price(), t.floorNo()))
                .toList();
        final Long asking = property.priceDeposit();
        if (isComputeGapRate(asking, list)) {
            return new ReferenceCardResponse(list, asking, null, null, lookbackMonths);
        }
        final long latest = list.getFirst().price();
        final BigDecimal gap = BigDecimal.valueOf((asking - latest) * 100.0 / latest).setScale(1, RoundingMode.HALF_UP);
        return new ReferenceCardResponse(list, asking, gap, null, lookbackMonths);
    }

    private static boolean isComputeGapRate(Long asking, List<ReferenceTransactionResponse> list) {
        return asking == null || list.isEmpty() || list.getFirst().price() == null || list.getFirst().price() <= 0;
    }
}
