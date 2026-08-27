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
}
