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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ReferenceTransactionService {

    /** 같은 타입으로 볼 전용면적 오차. */
    private static final double AREA_TOLERANCE = 0.15;
    /** 이보다 짧은 단지명은 우연히 걸린다 — 판정에 쓰지 않는다. */
    private static final int MIN_NAME_LENGTH = 2;

    private final PropertyRepository propertyRepository;
    private final ReferenceTransactionRepository referenceTransactionRepository;
    private final MinistryReferencePort ministryReferencePort;
    private final LegalDongCodeService legalDongCodeService;

    public ReferenceTransactionService(PropertyRepository propertyRepository,
                                       ReferenceTransactionRepository referenceTransactionRepository,
                                       MinistryReferencePort ministryReferencePort,
                                       LegalDongCodeService legalDongCodeService) {
        this.propertyRepository = propertyRepository;
        this.referenceTransactionRepository = referenceTransactionRepository;
        this.ministryReferencePort = ministryReferencePort;
        this.legalDongCodeService = legalDongCodeService;
    }

    public ReferenceCardResponse getReferences(Long propertyId, String legalDongCode, String dealMonth) {
        final Property property = propertyRepository.findById(propertyId)
                .orElseThrow(NotFoundListingsException::new);

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
            return new ReferenceCardResponse(List.of(), property.priceDeposit(), null, null);
        }

        final List<ReferenceTrade> trades = ministryReferencePort.fetchTrades(lawdCd, month);
        final List<ReferenceTransaction> saved = trades.stream()
                .filter(trade -> matches(property, trade))
                .sorted(Comparator.comparing(ReferenceTrade::contractDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .map(trade -> referenceTransactionRepository.save(new ReferenceTransaction(
                        null, propertyId, ReferenceDealType.TRADE, trade.contractDate(),
                        trade.dealAmount(), trade.areaM2(), trade.floorNo(),
                        ReferenceSource.MINISTRY_TRADE, Instant.now())))
                .toList();
        return toCard(property, saved);
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
            return new ReferenceCardResponse(list, asking, null, null);
        }
        final long latest = list.getFirst().price();
        final BigDecimal gap = BigDecimal.valueOf((asking - latest) * 100.0 / latest).setScale(1, RoundingMode.HALF_UP);
        return new ReferenceCardResponse(list, asking, gap, null);
    }

    private static boolean isComputeGapRate(Long asking, List<ReferenceTransactionResponse> list) {
        return asking == null || list.isEmpty() || list.getFirst().price() == null || list.getFirst().price() <= 0;
    }
}
