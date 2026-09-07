package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.loan.RegulationZone;
import banghak.home.halley.domain.regulation.RegulationNotice;

import java.util.Optional;

/** 규제지역 지정 고시 조회. */
public interface LawNoticePort {

 /** 설정이 갖춰져 실제로 호출할 수 있는지. */
    boolean isEnabled();

 /** 해당 규제의 현행 고시. 조회에 실패하면 {@link Optional#empty()}. */
    Optional<RegulationNotice> fetchLatest(RegulationZone zone);
}
