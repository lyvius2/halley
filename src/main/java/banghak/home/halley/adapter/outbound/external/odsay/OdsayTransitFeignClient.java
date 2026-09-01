package banghak.home.halley.adapter.outbound.external.odsay;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "odsay",
        url = "${odsay.base-url:https://api.odsay.com}",
        fallbackFactory = OdsayTransitFallbackFactory.class)
public interface OdsayTransitFeignClient {

    @GetMapping("${odsay.transit-path:/v1/api/searchPubTransPathT}")
    String findTransit(@RequestParam("apiKey") String apiKey,
                       @RequestParam("SX") double startX,
                       @RequestParam("SY") double startY,
                       @RequestParam("EX") double endX,
                       @RequestParam("EY") double endY);

    /**
     * 경로선 (설계 I177).
     *
     * <p>`mapObject` 는 <b>`0:0@` + mapObj</b> 형태입니다 — 앞의 좌표쌍은 화면 영역이고,
     * `0:0` 이면 전체를 줍니다. 실호출로 확인했습니다.
     */
    @GetMapping("${odsay.lane-path:/v1/api/loadLane}")
    String loadLane(@RequestParam("apiKey") String apiKey,
                    @RequestParam("mapObject") String mapObject);
}
