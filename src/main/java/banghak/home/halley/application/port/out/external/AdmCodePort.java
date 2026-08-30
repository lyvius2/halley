package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.geo.AdmArea;

import java.util.List;

/**
 * 행정구역 코드 목록 조회 (설계 I78).
 *
 * <p>규제지역 고시는 지역을 <b>이름으로</b> 적는데(`화성동탄`) 저장은 코드로 합니다. 그 사이를
 * 잇는 시군구 사전이 필요하고, `legal_dong_code`는 카카오로 채우는 지연 캐시라 기동 시 비어
 * 있습니다.
 *
 * <p><b>목록을 코드에 박지 않는 이유</b>는 행정구역이 실제로 바뀌기 때문입니다 — 화성시 동탄구가
 * 신설됐고 광주광역시와 전라남도가 통합됐습니다. 박아 둔 목록은 낡아도 낡은 줄 모릅니다.
 */
public interface AdmCodePort {

    boolean isEnabled();

    /** 시도 17곳 남짓. 코드는 2자리. */
    List<AdmArea> fetchSido();

    /** 한 시도의 시군구. 코드는 5자리. */
    List<AdmArea> fetchSigungu(String sidoCode);
}
