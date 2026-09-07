package banghak.home.halley.adapter.outbound.external.vworld;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** V-World 국가공간정보 개방데이터. 공시가격 속성조회. */
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
                               @RequestParam("pageNo") int pageNo);

    @GetMapping("/ned/data/getIndvdHousingPriceAttr")
    String fetchDetachedHousePrice(@RequestParam("key") String key,
                                   @RequestParam("pnu") String pnu,
                                   @RequestParam("stdrYear") String stdrYear,
                                   @RequestParam("format") String format,
                                   @RequestParam("numOfRows") int numOfRows,
                                   @RequestParam("pageNo") int pageNo);
}
