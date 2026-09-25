package banghak.home.halley.adapter.outbound.external.vworld;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "vworld-adm-code",
        url = "${vworld.base-url:https://api.vworld.kr}",
        fallbackFactory = VworldAdmCodeFallbackFactory.class)
public interface VworldAdmCodeFeignClient {

    /** 시도 목록. `admCode`를 줘도 무시하고 시도를 돌려준다.  */
    @GetMapping("/ned/data/admCodeList")
    String sido(@RequestParam("key") String key,
                @RequestParam("format") String format,
                @RequestParam("numOfRows") int numOfRows,
                @RequestParam("pageNo") int pageNo);

    /** @param admCode 시도 코드 2자리  */
    @GetMapping("/ned/data/admSiList")
    String sigungu(@RequestParam("key") String key,
                   @RequestParam("admCode") String admCode,
                   @RequestParam("format") String format,
                   @RequestParam("numOfRows") int numOfRows,
                   @RequestParam("pageNo") int pageNo);
}
