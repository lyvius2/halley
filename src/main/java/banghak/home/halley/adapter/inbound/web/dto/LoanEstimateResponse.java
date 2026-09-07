package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.loan.CollateralSource;
import banghak.home.halley.domain.loan.HouseOwnership;
import banghak.home.halley.domain.loan.JeonseEstimateResult;
import banghak.home.halley.domain.loan.LoanEstimateResult;
import banghak.home.halley.domain.loan.ProductType;
import banghak.home.halley.domain.loan.RegulationZone;

import java.math.BigDecimal;

/** 대출 한도 계산 결과. */
public record LoanEstimateResponse(
        Long propertyId,
        ProductType productType,
        String productLabel,
        Long finalLimit,
        Long requiredCash,
        Long monthlyPayment,
        Boolean interestOnly,
        Long dsrLimit,
        Long dsrCapacity,
        Long existingLoanAnnual,
        Long askingPrice,
        Long usedAnnualIncome,
        Long usedCash,
 /** 같은 그룹 사용자들의 보유 현금 합계. */
        Long groupCash,
        Long usedExistingLoan,
        Double monthlyRate,
 /** DSR 한도를 역산할 때 쓴 연이율. 실금리보다 높다 */
        Double dsrRate,
        String rateTypeLabel,
        Integer termMonths,
 /** 금리 출처 한 줄. 은행 12개 상품 변동금리 중앙값 (2026년 1월 공시) 또는 */
        String rateSource,
 /** 스트레스 금리가 어디서 왔는지. 한국은행 통계로 산출했으면 그 근거, */
        String stressRateSource,

        Long ltvLimit,
        Long acquisitionTax,
        Long collateralValue,
        CollateralSource collateralSource,
        String collateralSourceLabel,
        Integer collateralSampleCount,
        Boolean collateralReliable,
        Long leaseDeduction,
        Boolean mortgageInsured,
        RegulationZone zone,
        String zoneLabel,
        HouseOwnership ownership,
        String ownershipLabel,
        BigDecimal ltvRate,
        String ltvReason,
 /** 규제지역 값을 믿을 수 없을 때 그 사유. 규제지역이 비면 비규제로 판정되어 */
        String zoneWarning,

        Long guaranteeLimit,
        BigDecimal guaranteeRate,
        Long guaranteeCap
) {

    public static LoanEstimateResponse mortgage(Long propertyId, LoanEstimateResult r,
                                                long askingPrice, long annualIncome, long cash,
                                                long existingLoan, long groupCash, boolean insured,
                                                RegulationZone zone, HouseOwnership ownership,
                                                BigDecimal ltvRate, String ltvReason,
                                                String zoneWarning, String rateSource,
                                                String rateTypeLabel, String stressRateSource) {
        return new LoanEstimateResponse(
                propertyId, ProductType.MORTGAGE, "주택담보대출",
                r.finalLimit(), r.requiredCash(), r.monthlyPayment(), false,
                r.dsrLimit(), r.dsrCapacity(), r.existingLoanAnnual(),
                askingPrice, annualIncome, cash, groupCash, existingLoan,
                r.monthlyRate(), r.dsrMonthlyRate() * 12, rateTypeLabel, r.termMonths(), rateSource,
                stressRateSource,
                r.ltvLimit(), r.acquisitionTax(),
                r.collateralValue(), r.collateralSource(), r.collateralSource().label(),
                r.collateralSampleCount(), r.collateralReliable(),
                r.leaseDeduction(), insured,
                zone, zone.label(), ownership, ownership.label(), ltvRate, ltvReason, zoneWarning,
                null, null, null);
    }

    public static LoanEstimateResponse jeonse(Long propertyId, JeonseEstimateResult r,
                                              long deposit, long annualIncome, long cash,
                                              long existingLoan, long groupCash, String rateSource,
                                              String stressRateSource) {
        return new LoanEstimateResponse(
                propertyId, ProductType.JEONSE, "전세자금대출",
                r.finalLimit(), r.requiredCash(), r.monthlyPayment(), true,
                r.dsrLimit(), r.dsrCapacity(), r.existingLoanAnnual(),
                deposit, annualIncome, cash, groupCash, existingLoan,
                r.monthlyRate(), null, null, r.termMonths(), rateSource,
                stressRateSource,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                r.guaranteeLimit(), r.guaranteeRate(), r.guaranteeCap());
    }
}
