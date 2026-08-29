package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.property.OfficialPrice;

import java.util.List;

/**
 * 국토교통부 공시가격 조회 (V-World 개방데이터) — 설계 I54.
 * PNU(필지고유번호 19자리) 하나로 그 필지의 공시가격을 모두 받아 온다.
 */
public interface HousingPricePort {

    /** 공동주택(아파트·연립·다세대) 공시가격. 같은 필지의 동·호가 모두 나온다. */
    List<OfficialPrice> fetchApartmentPrices(String pnu);

    /** 개별주택(단독·다가구) 공시가격. 필지당 보통 1건이다. */
    List<OfficialPrice> fetchDetachedHousePrices(String pnu);
}
