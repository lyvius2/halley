package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.building.BuildingLedger;

import java.util.Optional;

public interface BuildingLedgerPort {

    /** 설정이 갖춰져 실제로 호출할 수 있는 상태인지.  */
    boolean isEnabled();

    Optional<BuildingLedger> fetchRecapTitle(String pnu);
}
