package banghak.home.halley.domain.building;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 건축물대장 총괄표제부.
 *
 * 단지 전체의 값입니다. 표제부(동별)가 아닙니다 — 표제부는 동마다 한 행이라
 * 대지면적이 동 수만큼 중복되고, 그대로 더하면 용적률이 엉뚱해집니다.
 *
 * @param floorAreaRatio 용적률(%). 대장이 이미 계산해 둔 값입니다 —
 *                       실측(동탄역시범호반써밋)에서 vlRatEstmTotArea / platArea × 100과
 *                       정확히 일치했습니다. 직접 계산하지 않고 이 값을 씁니다
 * @param approvedOn     사용승인일. 용적률 여유는 연식과 함께 봐야 의미가 있습니다 —
 *                       신축에 여유가 크다고 재건축 호재가 아닙니다
 * @param parkingCount   총 주차대수. 붙여넣기로 받은 세대당 주차를 검증할 수 있다
 */
public record BuildingLedger(
        String buildingName,
        BigDecimal landArea,
        BigDecimal floorAreaRatio,
        BigDecimal buildingCoverageRatio,
        Integer householdCount,
        Integer mainBuildingCount,
        Integer parkingCount,
        LocalDate approvedOn
) {

    /** 준공 후 몇 년 지났는가. 사용승인일을 모르면 null. */
    public Integer ageYears(LocalDate today) {
        return approvedOn == null ? null : java.time.Period.between(approvedOn, today).getYears();
    }
}
