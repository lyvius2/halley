package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.landuse.LandUse;

import java.util.List;

public interface LandUsePort {

    boolean isEnabled();

    List<LandUse> fetch(String pnu);
}
