package banghak.home.halley.adapter.outbound.external.vworld;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** V-World 토지이용계획 속성조회. */
@FeignClient(name = "vworld-land-use",
        url = "${vworld.base-url:https://api.vworld.kr}",
        fallbackFactory = VworldLandUseFallbackFactory.class)
public interface VworldLandUseFeignClient {

    @GetMapping("/ned/data/getLandUseAttr")
    String landUse(@RequestParam("key") String key,
                   @RequestParam("pnu") String pnu,
                   @RequestParam("format") String format,
                   @RequestParam("numOfRows") int numOfRows,
                   @RequestParam("pageNo") int pageNo);
}
