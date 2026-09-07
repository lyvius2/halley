package banghak.home.halley.adapter.outbound.external.vworld;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** V-World 행정구역 코드 조회. */
@FeignClient(name = "vworld-adm-code",
        url = "${vworld.base-url:https://api.vworld.kr}",
        fallbackFactory = VworldAdmCodeFallbackFactory.class)
public interface VworldAdmCodeFeignClient {

 /** 시도 목록. admCode를 줘도 무시하고 시도를 돌려준다. */
    @GetMapping("/ned/data/admCodeList")
    String sido(@RequestParam("key") String key,
                @RequestParam("format") String format,
                @RequestParam("numOfRows") int numOfRows,
                @RequestParam("pageNo") int pageNo);


    @GetMapping("/ned/data/admSiList")
    String sigungu(@RequestParam("key") String key,
                   @RequestParam("admCode") String admCode,
                   @RequestParam("format") String format,
                   @RequestParam("numOfRows") int numOfRows,
                   @RequestParam("pageNo") int pageNo);
}
