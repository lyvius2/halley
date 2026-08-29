package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.landuse.LandUse;

import java.util.List;

/**
 * 토지이용계획 조회 (V-World) — 설계 I69.
 * PNU(필지고유번호 19자리)로 그 필지에 걸린 지역·지구를 모두 받아 온다.
 */
public interface LandUsePort {

    boolean isEnabled();

    List<LandUse> fetch(String pnu);
}
