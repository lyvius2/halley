package banghak.home.halley.adapter.outbound.external.vworld;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * V-World 행정구역 코드 조회 (설계 I78).
 *
 * <p><b>엔드포인트가 계층마다 다릅니다.</b> `admCodeList`는 파라미터와 무관하게 <b>시도만</b>
 * 돌려주고, 시군구는 `admSiList`에 상위 코드를 넘겨야 나옵니다 — 실측으로 확인했습니다.
 */
@FeignClient(name = "vworld-adm-code",
        url = "${vworld.base-url:https://api.vworld.kr}",
        fallbackFactory = VworldAdmCodeFallbackFactory.class)
public interface VworldAdmCodeFeignClient {

    /** 시도 목록. `admCode`를 줘도 무시하고 시도를 돌려준다. */
    @GetMapping("/ned/data/admCodeList")
    String sido(@RequestParam("key") String key,
                @RequestParam("format") String format,
                @RequestParam("numOfRows") int numOfRows,
                @RequestParam("pageNo") int pageNo);

    /** @param admCode 시도 코드 2자리 */
    @GetMapping("/ned/data/admSiList")
    String sigungu(@RequestParam("key") String key,
                   @RequestParam("admCode") String admCode,
                   @RequestParam("format") String format,
                   @RequestParam("numOfRows") int numOfRows,
                   @RequestParam("pageNo") int pageNo);
}
