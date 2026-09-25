package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.cache.MarketRateCache;
import banghak.home.halley.application.port.out.external.FinanceProductPort;
import banghak.home.halley.domain.finance.FinanceGroup;
import banghak.home.halley.domain.finance.LoanProduct;
import banghak.home.halley.domain.finance.LoanProductType;
import banghak.home.halley.domain.finance.LoanRateOption;
import banghak.home.halley.domain.finance.MarketRate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class MarketRateService {

    private static final FinanceGroup GROUP = FinanceGroup.BANK;
    /** 아파트 담보. 금감원 `mrtg_type_nm`이 쓰는 표기다.  */
    private static final String APARTMENT = "아파트";
    /** 대표성이 이보다 적으면 시장값이라 부르기 어렵다.  */
    private static final int MIN_SAMPLES = 3;

    private final FinanceProductPort financeProductPort;
    private final MarketRateCache marketRateCache;
    /** 고정/변동 중 무엇을 대표로 볼지. 대부분 변동으로 받으므로 기본은 변동이다.  */
    private final String preferredRateType;

    public MarketRateService(FinanceProductPort financeProductPort,
                             MarketRateCache marketRateCache,
                             @Value("${loan.market-rate.type:변동}") String preferredRateType) {
        this.financeProductPort = financeProductPort;
        this.marketRateCache = marketRateCache;
        this.preferredRateType = preferredRateType;
    }

    /** 캐시 우선. 없으면 조회해 담는다. 못 받으면 비어 있고 호출 측이 기본 금리로 떨어진다.  */
    public Optional<MarketRate> find(LoanProductType type) {
        return marketRateCache.get(type).or(() -> refresh(type));
    }

    /** 하루 한 번 배치가 부른다. 캐시를 무시하고 새로 받는다.  */
    public Optional<MarketRate> refresh(LoanProductType type) {
        if (!financeProductPort.isEnabled()) {
            log.info("Skipping market rate lookup - fss.api-key not configured. type={}", type);
            return Optional.empty();
        }
        final List<LoanProduct> products = financeProductPort.fetchLoanProducts(type, GROUP);
        if (products.isEmpty()) {
            log.warn("No market rate data - falling back to the configured rate. type={}", type);
            return Optional.empty();
        }
        final List<BigDecimal> rates = representativeRates(products, type);
        if (rates.size() < MIN_SAMPLES) {
            log.warn("Too few market rate samples - falling back to the configured rate. "
                    + "type={}, samples={}, minimum={}", type, rates.size(), MIN_SAMPLES);
            return Optional.empty();
        }
        final MarketRate rate = new MarketRate(
                median(rates), type, preferredRateType, rates.size(), dclsMonthOf(products));
        marketRateCache.put(rate);
        log.info("Market rate resolved. type={}, rate={}, samples={}, dclsMonth={}",
                type, rate.rate(), rate.sampleCount(), rate.dclsMonth());
        return Optional.of(rate);
    }

    private List<BigDecimal> representativeRates(List<LoanProduct> products, LoanProductType type) {
        final List<BigDecimal> rates = new ArrayList<>();
        for (final LoanProduct product : products) {
            product.options().stream()
                    .filter(o -> matches(o, type))
                    .map(LoanRateOption::representativeRate)
                    .filter(r -> r != null && r.signum() > 0)
                    .min(Comparator.naturalOrder())
                    .ifPresent(rates::add);
        }
        return rates;
    }

    private boolean matches(LoanRateOption option, LoanProductType type) {
        if (!contains(option.rateTypeName(), preferredRateType)) {
            return false;
        }
        // 전세자금대출에는 담보유형이 없다 — 있는 쪽만 아파트로 좁힌다
        return type != LoanProductType.MORTGAGE || contains(option.mortgageTypeName(), APARTMENT);
    }

    private boolean contains(String value, String keyword) {
        return value != null && keyword != null && value.contains(keyword);
    }

    private BigDecimal median(List<BigDecimal> rates) {
        final List<BigDecimal> sorted = rates.stream().sorted().toList();
        final int size = sorted.size();
        final BigDecimal percent = size % 2 == 1
                ? sorted.get(size / 2)
                : sorted.get(size / 2 - 1).add(sorted.get(size / 2))
                        .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        // 공시는 퍼센트(3.62)로 오고 계산은 소수(0.0362)로 한다
        return percent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    }

    /** 공시월은 상품마다 같지만, 섞여 오면 가장 최근 것을 쓴다.  */
    private String dclsMonthOf(List<LoanProduct> products) {
        return products.stream()
                .map(LoanProduct::dclsMonth)
                .filter(m -> m != null && !m.isBlank())
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
