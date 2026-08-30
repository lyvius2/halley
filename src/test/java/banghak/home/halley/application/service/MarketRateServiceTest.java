package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.cache.InMemoryMarketRateCache;
import banghak.home.halley.application.port.out.external.FinanceProductPort;
import banghak.home.halley.domain.finance.FinanceCompany;
import banghak.home.halley.domain.finance.FinanceGroup;
import banghak.home.halley.domain.finance.LoanProduct;
import banghak.home.halley.domain.finance.LoanProductType;
import banghak.home.halley.domain.finance.LoanRateOption;
import banghak.home.halley.domain.finance.MarketRate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("금감원 공시에서 대표 금리 뽑기 (설계 I81)")
class MarketRateServiceTest {

    private final InMemoryMarketRateCache cache = new InMemoryMarketRateCache();

    @Test
    @DisplayName("평균이 아니라 중앙값을 쓴다 — 특판 하나에 끌려가면 실제와 멀어진다")
    void usesMedianNotAverage() {
        // given — 0.5%짜리 특판이 섞여 있다. 평균이면 2.9%대로 내려간다
        final MarketRateService service = service(products(
                option("3.50"), option("3.60"), option("3.70"), option("0.50")));

        // when
        final Optional<MarketRate> rate = service.refresh(LoanProductType.MORTGAGE);

        // then — 정렬하면 0.50 3.50 3.60 3.70, 중앙값은 (3.50+3.60)/2 = 3.55
        assertThat(rate).isPresent();
        assertThat(rate.get().rate()).isEqualByComparingTo("0.035500");
        assertThat(rate.get().sampleCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("주담대는 아파트 담보·변동금리 옵션만 센다")
    void filtersByMortgageTypeAndRateType() {
        // given
        final LoanProduct product = new LoanProduct(
                LoanProductType.MORTGAGE, FinanceGroup.BANK, "202601", "0010001", "우리은행",
                "P1", "우리아파트론", null, null, null, null, "LTV 70% 이내",
                List.of(
                        new LoanRateOption("A", "아파트", "D", "분할상환", "C", "변동금리",
                                null, null, new BigDecimal("3.20")),
                        new LoanRateOption("A", "아파트", "D", "분할상환", "F", "고정금리",
                                null, null, new BigDecimal("9.90")),
                        new LoanRateOption("B", "주택외", "D", "분할상환", "C", "변동금리",
                                null, null, new BigDecimal("8.80"))));
        final List<LoanProduct> three = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            three.add(product);
        }

        // when
        final Optional<MarketRate> rate = service(three).refresh(LoanProductType.MORTGAGE);

        // then — 고정금리 9.9와 주택외 8.8은 빠지고 3.20만 남는다
        assertThat(rate).isPresent();
        assertThat(rate.get().rate()).isEqualByComparingTo("0.032000");
    }

    @Test
    @DisplayName("전세는 담보유형이 없어도 걸러내지 않는다")
    void doesNotRequireMortgageTypeForJeonse() {
        // given — rentHouseLoanProductsSearch 옵션에는 mrtg_type이 없다
        final LoanProduct product = new LoanProduct(
                LoanProductType.JEONSE, FinanceGroup.BANK, "202601", "0010001", "우리은행",
                "J1", "우리전세론", null, null, null, null, null,
                List.of(new LoanRateOption(null, null, "S", "만기일시상환", "C", "변동금리",
                        null, null, new BigDecimal("3.90"))));

        // when
        final Optional<MarketRate> rate =
                service(List.of(product, product, product)).refresh(LoanProductType.JEONSE);

        // then
        assertThat(rate).isPresent();
        assertThat(rate.get().rate()).isEqualByComparingTo("0.039000");
    }

    @Test
    @DisplayName("표본이 너무 적으면 시장값으로 보지 않는다 — 기본 금리로 떨어진다")
    void requiresEnoughSamples() {
        // when — 2건뿐
        final Optional<MarketRate> rate =
                service(products(option("3.50"), option("3.60"))).refresh(LoanProductType.MORTGAGE);

        // then
        assertThat(rate).isEmpty();
    }

    @Test
    @DisplayName("조회에 실패해도 예외 대신 빈 값 — 대출 계산 자체는 계속 돌아야 한다")
    void survivesLookupFailure() {
        assertThat(service(List.of()).refresh(LoanProductType.MORTGAGE)).isEmpty();
    }

    @Test
    @DisplayName("캐시에 있으면 다시 부르지 않는다 — 일 허용횟수가 있는 API다")
    void servesFromCache() {
        // given
        final AtomicInteger calls = new AtomicInteger();
        final MarketRateService service = new MarketRateService(
                countingPort(calls, products(option("3.50"), option("3.60"), option("3.70"))),
                cache, "변동");

        // when
        service.find(LoanProductType.MORTGAGE);
        service.find(LoanProductType.MORTGAGE);
        service.find(LoanProductType.MORTGAGE);

        // then
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("금리 출처를 사람이 읽을 문장으로 만든다")
    void describesSource() {
        final MarketRate rate = new MarketRate(
                new BigDecimal("0.0362"), LoanProductType.MORTGAGE, "변동", 12, "202601");

        assertThat(rate.describe()).isEqualTo("은행 12개 상품 변동 중앙값 (2026년 1월 공시)");
    }

    private MarketRateService service(List<LoanProduct> products) {
        return new MarketRateService(port(products), new InMemoryMarketRateCache(), "변동");
    }

    private List<LoanProduct> products(LoanRateOption... options) {
        final List<LoanProduct> products = new ArrayList<>();
        int i = 0;
        for (final LoanRateOption option : options) {
            products.add(new LoanProduct(
                    LoanProductType.MORTGAGE, FinanceGroup.BANK, "202601", "001000" + i,
                    "은행" + i, "P" + i, "상품" + i, null, null, null, null, null,
                    List.of(option)));
            i++;
        }
        return products;
    }

    private LoanRateOption option(String avg) {
        return new LoanRateOption("A", "아파트", "D", "분할상환", "C", "변동금리",
                null, null, new BigDecimal(avg));
    }

    private FinanceProductPort port(List<LoanProduct> products) {
        return countingPort(new AtomicInteger(), products);
    }

    private FinanceProductPort countingPort(AtomicInteger calls, List<LoanProduct> products) {
        return new FinanceProductPort() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public List<LoanProduct> fetchLoanProducts(LoanProductType type, FinanceGroup group) {
                calls.incrementAndGet();
                return products;
            }

            @Override
            public List<FinanceCompany> fetchCompanies(FinanceGroup group) {
                return List.of();
            }
        };
    }
}
