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
        /**
         * 같은 그룹 사용자들의 보유 현금 합계 (설계 I114).
         *
         * <p>한도 계산에는 <b>내 현금만</b> 들어갑니다 — 대출은 개인 명의로 받으니까요.
         * 그런데 이 앱은 그룹이 현금을 모아 집을 사려고 만든 것이라, 화면에는 둘 다 보여야
         * "왜 내 현금 기준으로 부족하다고 나오는가"를 오해 없이 읽을 수 있습니다.
         */
        Long groupCash,
        Long usedExistingLoan,
        Double monthlyRate,
        /** DSR 한도를 역산할 때 쓴 연이율 (설계 I97). 실금리보다 높다 */
        Double dsrRate,
        String rateTypeLabel,
        Integer termMonths,
        /**
         * 금리 출처 한 줄 (설계 I81). `은행 12개 상품 변동금리 중앙값 (2026년 1월 공시)` 또는
         * 못 받았을 때 `기본 금리 4% 적용 중`. 어디서 온 숫자인지 안 보이면 검증할 수 없다
         */
        String rateSource,
        /**
         * 스트레스 금리가 어디서 왔는지 (설계 I116). 한국은행 통계로 산출했으면 그 근거,
         * 사람이 넣은 값이면 null. <b>한도를 좁히는 숫자라 출처가 보여야 검증됩니다.</b>
         */
        String stressRateSource,

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
        /**
         * 규제지역 값을 믿을 수 없을 때 그 사유 (설계 I73). 규제지역이 비면 비규제로 판정되어
         * LTV 0.7이 잡히는데, 실제가 투기과열지구(0.4)면 <b>한도를 과대평가</b>한다.
         * 조용히 틀리지 않도록 화면에 그대로 실어 보낸다. 정상이면 null
         */
        String zoneWarning,

        // ── 전세 전용 ────────────────────────────
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
