package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.loan.CollateralSource;
import banghak.home.halley.domain.loan.HouseOwnership;
import banghak.home.halley.domain.loan.JeonseEstimateResult;
import banghak.home.halley.domain.loan.LoanEstimateResult;
import banghak.home.halley.domain.loan.ProductType;
import banghak.home.halley.domain.loan.RegulationZone;

import java.math.BigDecimal;

/**
 * 대출 한도 계산 결과 (설계 3.4 · I55 · I64 · I67).
 *
 * <p><b>`productType`이 어느 필드를 읽을지 정합니다.</b> 매매(주담대)와 전세는 계산 구조가 달라
 * 결과 항목도 다릅니다. 한 화면이 두 상품을 다루므로 하나의 DTO로 내보내되, 해당 없는 필드는
 * null입니다.
 *
 * <ul>
 *   <li><b>MORTGAGE</b> — `ltvLimit`·`collateral*`·`leaseDeduction`·`acquisitionTax`·`zone`·`ltvRate`</li>
 *   <li><b>JEONSE</b> — `guaranteeLimit`·`guaranteeRate`·`guaranteeCap`.
 *       취득세·방공제·LTV·담보가치가 <b>없습니다</b> — 담보가 집이 아니라 보증기관의 보증입니다</li>
 * </ul>
 *
 * <p>뒤쪽 공통 필드들은 <b>화면의 대출액 슬라이더</b>가 매번 서버를 부르지 않고 월 상환액·필요
 * 현금을 다시 계산하기 위한 값입니다.
 *
 * @param interestOnly 이자만 내는 상품인지. 전세대출은 만기일시상환이라 true
 */
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
        Long usedExistingLoan,
        Double monthlyRate,
        Integer termMonths,

        // ── 매매(주담대) 전용 ─────────────────────
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

        // ── 전세 전용 ────────────────────────────
        Long guaranteeLimit,
        BigDecimal guaranteeRate,
        Long guaranteeCap
) {

    public static LoanEstimateResponse mortgage(Long propertyId, LoanEstimateResult r,
                                                long askingPrice, long annualIncome, long cash,
                                                long existingLoan, boolean insured,
                                                RegulationZone zone, HouseOwnership ownership,
                                                BigDecimal ltvRate, String ltvReason) {
        return new LoanEstimateResponse(
                propertyId, ProductType.MORTGAGE, "주택담보대출",
                r.finalLimit(), r.requiredCash(), r.monthlyPayment(), false,
                r.dsrLimit(), r.dsrCapacity(), r.existingLoanAnnual(),
                askingPrice, annualIncome, cash, existingLoan,
                r.monthlyRate(), r.termMonths(),
                r.ltvLimit(), r.acquisitionTax(),
                r.collateralValue(), r.collateralSource(), r.collateralSource().label(),
                r.collateralSampleCount(), r.collateralReliable(),
                r.leaseDeduction(), insured,
                zone, zone.label(), ownership, ownership.label(), ltvRate, ltvReason,
                null, null, null);
    }

    public static LoanEstimateResponse jeonse(Long propertyId, JeonseEstimateResult r,
                                              long deposit, long annualIncome, long cash,
                                              long existingLoan) {
        return new LoanEstimateResponse(
                propertyId, ProductType.JEONSE, "전세자금대출",
                r.finalLimit(), r.requiredCash(), r.monthlyPayment(), true,
                r.dsrLimit(), r.dsrCapacity(), r.existingLoanAnnual(),
                deposit, annualIncome, cash, existingLoan,
                r.monthlyRate(), r.termMonths(),
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                r.guaranteeLimit(), r.guaranteeRate(), r.guaranteeCap());
    }
}
