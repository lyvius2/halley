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
            log.info("법정동코드를 찾지 못해 실거래가를 조회하지 않습니다. propertyId={}, 지번주소={} "
                    + "(legal_dong_code 적재 범위를 확인하세요)", propertyId, property.addressJibun());
            return new ReferenceCardResponse(List.of(), property.priceDeposit(), null, null);
        }

        final List<ReferenceTrade> trades = ministryReferencePort.fetchTrades(lawdCd, month);
        final List<ReferenceTransaction> saved = trades.stream()
                .filter(trade -> matches(property, trade))
                .sorted(Comparator.comparing(ReferenceTrade::contractDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .map(trade -> referenceTransactionRepository.save(new ReferenceTransaction(
                        null, propertyId, ReferenceDealType.TRADE, trade.contractDate(),
                        trade.dealAmount(), trade.floorNo(), ReferenceSource.MINISTRY_TRADE, Instant.now())))
                .toList();
        return toCard(property, saved);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean matches(Property property, ReferenceTrade trade) {
        if (property.name() != null && trade.apartmentName() != null
                && property.name().equalsIgnoreCase(trade.apartmentName())) {
            return true;
        }
        if (property.areaExclusiveM2() != null && trade.areaM2() != null
                && property.areaExclusiveM2().signum() > 0) {
            final double diff = Math.abs(property.areaExclusiveM2().doubleValue() - trade.areaM2().doubleValue());
            return diff / property.areaExclusiveM2().doubleValue() <= 0.15;
        }
        return false;
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
