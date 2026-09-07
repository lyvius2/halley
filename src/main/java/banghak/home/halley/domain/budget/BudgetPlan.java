package banghak.home.halley.domain.budget;

import java.math.BigDecimal;
import java.time.Instant;

/** 그룹이 저장하는 신혼 생활 시작 예산 계획. 금액은 모두 원 단위다. */
public record BudgetPlan(
        Long id,
        Long groupId,
        Long createdBy,
        String planName,
        BudgetScenario scenario,
        Long selectedPropertyId,
        HousingType housingType,
        String region,
        String houseName,
        long purchasePrice,
        BigDecimal exclusiveAreaM2,
        long contractCash,
        long balanceCash,
        long acquisitionTax,
        long brokerageFee,
        long registrationFee,
        long movingCost,
        long cleaningCost,
        CostInputSource acquisitionTaxSource,
        CostInputSource brokerageFeeSource,
        CostInputSource registrationFeeSource,
        CostInputSource movingCostSource,
        CostInputSource cleaningCostSource,
        long otherInitialCost,
        long parentSupport,
        long otherFunds,
        long monthlyManagementFee,
        long monthlyOtherHousingCost,
        Instant createdAt,
        Instant updatedAt
) {

    public BudgetPlan {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId is required");
        }
        if (planName == null || planName.isBlank()) {
            throw new IllegalArgumentException("planName is required");
        }
        scenario = scenario == null ? BudgetScenario.RECOMMENDED : scenario;
        housingType = housingType == null ? HousingType.SALE : housingType;
        acquisitionTaxSource = acquisitionTaxSource == null ? CostInputSource.MANUAL : acquisitionTaxSource;
        brokerageFeeSource = brokerageFeeSource == null ? CostInputSource.MANUAL : brokerageFeeSource;
        registrationFeeSource = registrationFeeSource == null ? CostInputSource.MANUAL : registrationFeeSource;
        movingCostSource = movingCostSource == null ? CostInputSource.MANUAL : movingCostSource;
        cleaningCostSource = cleaningCostSource == null ? CostInputSource.MANUAL : cleaningCostSource;
        validateNonNegative(purchasePrice, "purchasePrice");
        validateNonNegative(contractCash, "contractCash");
        validateNonNegative(balanceCash, "balanceCash");
        validateNonNegative(acquisitionTax, "acquisitionTax");
        validateNonNegative(brokerageFee, "brokerageFee");
        validateNonNegative(registrationFee, "registrationFee");
        validateNonNegative(movingCost, "movingCost");
        validateNonNegative(cleaningCost, "cleaningCost");
        validateNonNegative(otherInitialCost, "otherInitialCost");
        validateNonNegative(parentSupport, "parentSupport");
        validateNonNegative(otherFunds, "otherFunds");
        validateNonNegative(monthlyManagementFee, "monthlyManagementFee");
        validateNonNegative(monthlyOtherHousingCost, "monthlyOtherHousingCost");
    }

    private static void validateNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    public BudgetPlan(Long id, Long groupId, Long createdBy, String planName, BudgetScenario scenario,
                      Long selectedPropertyId, HousingType housingType, String region, String houseName,
                      long purchasePrice, BigDecimal exclusiveAreaM2, long contractCash, long balanceCash,
                      long acquisitionTax, long brokerageFee, long registrationFee, long movingCost,
                      long cleaningCost, long otherInitialCost, long parentSupport, long otherFunds,
                      long monthlyManagementFee, long monthlyOtherHousingCost, Instant createdAt, Instant updatedAt) {
        this(id, groupId, createdBy, planName, scenario, selectedPropertyId, housingType, region, houseName,
                purchasePrice, exclusiveAreaM2, contractCash, balanceCash, acquisitionTax, brokerageFee,
                registrationFee, movingCost, cleaningCost, CostInputSource.MANUAL, CostInputSource.MANUAL,
                CostInputSource.MANUAL, CostInputSource.MANUAL, CostInputSource.MANUAL, otherInitialCost,
                parentSupport, otherFunds, monthlyManagementFee, monthlyOtherHousingCost, createdAt, updatedAt);
    }
}
