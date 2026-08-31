package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.building.BuildingLedger;

import java.util.Optional;

/**
 * 건축물대장 (설계 I132).
 *
 * <p>재건축 여력(`조례 상한 용적률 − 현재 용적률`)을 <b>추정이 아니라 실측</b>으로
 * 구하려는 것입니다.
 *
 * <p>못 받으면 {@code empty}입니다. <b>근사값으로 채우지 않습니다</b> — 재건축 사업성은
 * 이 앱에서 가장 크게 틀릴 수 있는 숫자입니다.
 */
public interface BuildingLedgerPort {

    /** 설정이 갖춰져 실제로 호출할 수 있는 상태인지. */
    boolean isEnabled();

    /**
     * @param pnu 19자리. 시군구·법정동·산여부·본번·부번으로 쪼개 넘긴다 (설계 I54)
     */
    Optional<BuildingLedger> fetchRecapTitle(String pnu);
}
