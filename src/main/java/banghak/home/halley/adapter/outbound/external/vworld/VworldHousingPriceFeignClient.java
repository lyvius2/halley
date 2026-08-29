package banghak.home.halley.adapter.outbound.external.vworld;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * V-World 국가공간정보 개방데이터 — 공시가격 속성조회 (설계 I54).
 *
 * <p>응답은 `format=json`으로 받고, 인증 실패도 HTTP 200 + `resultCode: INVALID_KEY` 본문으로 온다.
 * `stdrYear`를 빼면 <b>그 필지의 전 연도가 오래된 순으로</b> 나오므로 반드시 연도를 지정한다.
 * `domain`은 키 발급 시 등록한 URL이다. 도메인 제한이 걸린 키는 이 값이 없으면 자료가 0건으로 나올 수 있어
 * 설정돼 있을 때만 붙인다(null이면 Feign이 쿼리에서 뺀다).
 */
@FeignClient(name = "vworld-housing-price",
        url = "${vworld.base-url:https://api.vworld.kr}",
        fallbackFactory = VworldHousingPriceFallbackFactory.class)
public interface VworldHousingPriceFeignClient {

    @GetMapping("/ned/data/getApartHousingPriceAttr")
    String fetchApartmentPrice(@RequestParam("key") String key,
                               @RequestParam("pnu") String pnu,
                               @RequestParam("stdrYear") String stdrYear,
                               @RequestParam("format") String format,
                               @RequestParam("numOfRows") int numOfRows,
                               @RequestParam("pageNo") int pageNo,
                               @RequestParam("domain") String domain);

    @GetMapping("/ned/data/getIndvdHousingPriceAttr")
    String fetchDetachedHousePrice(@RequestParam("key") String key,
                                   @RequestParam("pnu") String pnu,
                                   @RequestParam("stdrYear") String stdrYear,
                                   @RequestParam("format") String format,
                                   @RequestParam("numOfRows") int numOfRows,
                                   @RequestParam("pageNo") int pageNo,
                                   @RequestParam("domain") String domain);
}
