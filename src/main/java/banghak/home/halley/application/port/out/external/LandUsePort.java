package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.landuse.LandUse;

import java.util.List;

/** 토지이용계획 조회 (V-World). */
public interface LandUsePort {

    boolean isEnabled();

    List<LandUse> fetch(String pnu);
}
