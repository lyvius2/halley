package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.loan.RegulationZone;
import banghak.home.halley.domain.regulation.RegulationNotice;

import java.util.Optional;

/**
 * 규제지역 지정 고시 조회 (설계 I73).
 *
 * <p>토지이용계획 API에는 투기과열지구·조정대상지역이 없다는 것을 대조 실험으로 확인했고(I69),
 * 자동으로 얻을 수 있는 경로는 <b>법제처 국가법령정보</b>뿐입니다.
 */
public interface LawNoticePort {

    /** 설정이 갖춰져 실제로 호출할 수 있는지. */
    boolean isEnabled();

    /** 해당 규제의 현행 고시. 조회에 실패하면 {@link Optional#empty()}. */
    Optional<RegulationNotice> fetchLatest(RegulationZone zone);
}
