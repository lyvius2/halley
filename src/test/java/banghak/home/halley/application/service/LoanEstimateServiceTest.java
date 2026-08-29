package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateHistoryResponse;
import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateRequest;
import banghak.home.halley.domain.loan.HouseOwnership;
import banghak.home.halley.domain.loan.ProductType;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.loan.RegulationZone;
import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.outbound.persistence.LoanEstimateRepository;
import banghak.home.halley.domain.property.DealType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class LoanEstimateServiceTest {

    @Autowired
    private LoanEstimateService loanEstimateService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private LoanEstimateRepository loanEstimateRepository;

    @Test
    @DisplayName("대출 시뮬레이션 결과를 계산하고 저장한다")
    void estimate() {
        // given
        final PropertyResponse property = propertyService.create(request("대출 테스트", 800_000_000L));

        // when
        final LoanEstimateResponse result = loanEstimateService.estimate(property.id(),
                new LoanEstimateRequest(50_000_000L, 300_000_000L, null, true, true, 0));

        // then — 생애최초는 우대 LTV 80%가 붙지만 총액 상한 6억에 걸린다 (설계 I66)
        // 8억 × 80% = 6.4억 → 상한 6억. MCI 가입이라 방공제는 없다
        assertThat(result.ltvLimit()).isEqualTo(600_000_000L);
        assertThat(result.ltvRate()).isEqualByComparingTo("0.8");
        assertThat(result.ltvReason()).contains("생애최초");
        assertThat(result.finalLimit()).isLessThanOrEqualTo(result.ltvLimit());
        assertThat(result.requiredCash()).isEqualTo(800_000_000L - result.finalLimit());
        assertThat(result.acquisitionTax()).isEqualTo(9_333_333L);
        assertThat(loanEstimateRepository.findByPropertyId(property.id())).isNotEmpty();

        // history
        final List<LoanEstimateHistoryResponse> history = loanEstimateService.history(property.id());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().finalLimit()).isEqualTo(result.finalLimit());
    }

    @Test
    @DisplayName("비규제·무주택은 LTV 70%가 붙는다 (설계 I66)")
    void normalZoneNoHouse() {
        // given
        final PropertyResponse property = propertyService.create(request("비규제 무주택", 800_000_000L));

        // when — 생애최초 아님, 무주택
        final LoanEstimateResponse result = loanEstimateService.estimate(property.id(),
                new LoanEstimateRequest(50_000_000L, 300_000_000L, null, false, true, 0));

        // then — 규제지역 등록이 없으면 비규제로 본다. 8억 × 70% = 5.6억
        assertThat(result.zone()).isEqualTo(RegulationZone.NORMAL);
        assertThat(result.ownership()).isEqualTo(HouseOwnership.NONE);
        assertThat(result.ltvRate()).isEqualByComparingTo("0.7");
        assertThat(result.ltvLimit()).isEqualTo(560_000_000L);
    }

    @Test
    @DisplayName("보유 주택이 늘면 LTV가 낮아진다")
    void ownershipLowersLtv() {
        // given
        final PropertyResponse property = propertyService.create(request("보유주택 비교", 800_000_000L));

        // when
        final LoanEstimateResponse none = loanEstimateService.estimate(property.id(),
                new LoanEstimateRequest(50_000_000L, 300_000_000L, null, false, true, 0));
        final LoanEstimateResponse one = loanEstimateService.estimate(property.id(),
                new LoanEstimateRequest(50_000_000L, 300_000_000L, null, false, true, 1));
        final LoanEstimateResponse multi = loanEstimateService.estimate(property.id(),
                new LoanEstimateRequest(50_000_000L, 300_000_000L, null, false, true, 3));

        // then — 비규제 기준 무주택 70% / 1주택·다주택 60%
        assertThat(none.ltvLimit()).isGreaterThan(one.ltvLimit());
        assertThat(one.ownership()).isEqualTo(HouseOwnership.ONE);
        assertThat(multi.ownership()).isEqualTo(HouseOwnership.MULTI);
    }

    @Test
    @DisplayName("보유 주택 수를 비우면 무주택으로 본다")
    void nullOwnershipMeansNoHouse() {
        // given
        final PropertyResponse property = propertyService.create(request("보유수 미입력", 800_000_000L));

        // when
        final LoanEstimateResponse result = loanEstimateService.estimate(property.id(),
                new LoanEstimateRequest(50_000_000L, 300_000_000L, null, false, true, null));

        // then
        assertThat(result.ownership()).isEqualTo(HouseOwnership.NONE);
    }

    @Test
    @DisplayName("전세 매물은 전세자금대출로 계산한다 — 취득세·방공제가 나오지 않는다 (설계 I67)")
    void jeonsePropertyUsesJeonseLoan() {
        // given — 보증금 3억 전세
        final PropertyResponse property = propertyService.create(
                jeonseRequest("전세 매물", 300_000_000L));

        // when
        final LoanEstimateResponse result = loanEstimateService.estimate(property.id(),
                new LoanEstimateRequest(80_000_000L, 100_000_000L, null, false, false, 0));

        // then — 전세는 소유권이 넘어오지 않아 취득세가 없고, 담보가 보증이라 방공제·LTV가 없다
        assertThat(result.productType()).isEqualTo(ProductType.JEONSE);
        assertThat(result.productLabel()).isEqualTo("전세자금대출");
        assertThat(result.acquisitionTax()).isNull();
        assertThat(result.leaseDeduction()).isNull();
        assertThat(result.ltvLimit()).isNull();
        assertThat(result.collateralValue()).isNull();
        assertThat(result.zone()).isNull();
        // 보증 한도 = 3억 × 80% = 2.4억이지만 기관 한도 2.22억에 걸린다
        assertThat(result.guaranteeLimit()).isEqualTo(222_000_000L);
        assertThat(result.guaranteeRate()).isEqualByComparingTo("0.8");
        assertThat(result.interestOnly()).isTrue();
    }

    @Test
    @DisplayName("월세 매물도 보증금 기준으로 전세자금대출로 계산한다")
    void monthlyPropertyAlsoUsesJeonseLoan() {
        // given — 보증금 1억 / 월세 80만원
        final PropertyResponse property = propertyService.create(
                monthlyRequest("월세 매물", 100_000_000L, 800_000L));

        // when
        final LoanEstimateResponse result = loanEstimateService.estimate(property.id(),
                new LoanEstimateRequest(80_000_000L, 50_000_000L, null, false, false, 0));

        // then — 보증금 1억 × 80% = 8천만
        assertThat(result.productType()).isEqualTo(ProductType.JEONSE);
        assertThat(result.guaranteeLimit()).isEqualTo(80_000_000L);
        assertThat(result.askingPrice()).isEqualTo(100_000_000L);
    }

    @Test
    @DisplayName("매매 매물은 그대로 주담대로 계산한다")
    void salePropertyStillUsesMortgage() {
        // given
        final PropertyResponse property = propertyService.create(request("매매 매물", 800_000_000L));

        // when
        final LoanEstimateResponse result = loanEstimateService.estimate(property.id(),
                new LoanEstimateRequest(80_000_000L, 300_000_000L, null, false, true, 0));

        // then
        assertThat(result.productType()).isEqualTo(ProductType.MORTGAGE);
        assertThat(result.ltvLimit()).isNotNull();
        assertThat(result.acquisitionTax()).isNotNull();
        assertThat(result.guaranteeLimit()).isNull();
        assertThat(result.interestOnly()).isFalse();
    }

    @Test
    @DisplayName("전세 산정도 이력에 남고 상품 유형이 구분된다")
    void jeonseEstimateIsRecorded() {
        // given
        final PropertyResponse property = propertyService.create(
                jeonseRequest("이력 확인", 300_000_000L));

        // when
        loanEstimateService.estimate(property.id(),
                new LoanEstimateRequest(80_000_000L, 100_000_000L, null, false, false, 0));

        // then
        assertThat(loanEstimateRepository.findByPropertyId(property.id()))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.productType()).isEqualTo(ProductType.JEONSE);
                    assertThat(e.acquisitionTax()).isZero();
                });
    }

    private PropertyRequest jeonseRequest(String name, Long deposit) {
        return dealRequest(name, DealType.JEONSE, deposit, null);
    }

    private PropertyRequest monthlyRequest(String name, Long deposit, Long monthly) {
        return dealRequest(name, DealType.MONTHLY, deposit, monthly);
    }

    private PropertyRequest dealRequest(String name, DealType dealType, Long deposit, Long monthly) {
        return new PropertyRequest(
                name, null, dealType, deposit, monthly, null,
                "서울시", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null);
    }

    private PropertyRequest request(String name, Long priceDeposit) {
        return new PropertyRequest(
                name, null, DealType.SALE, priceDeposit, null, null,
                "서울시", null, null, null,
                null, null, null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
