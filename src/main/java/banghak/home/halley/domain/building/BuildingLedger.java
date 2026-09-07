package banghak.home.halley.domain.building;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 건축물대장 총괄표제부. */
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
