package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.geo.AdmArea;

import java.util.List;

public interface AdmCodePort {

    boolean isEnabled();

    /** 시도 17곳 남짓. 코드는 2자리.  */
    List<AdmArea> fetchSido();

    /** 한 시도의 시군구. 코드는 5자리.  */
    List<AdmArea> fetchSigungu(String sidoCode);
}
