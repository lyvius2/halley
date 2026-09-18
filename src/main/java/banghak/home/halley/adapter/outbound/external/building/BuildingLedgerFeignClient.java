package banghak.home.halley.adapter.outbound.external.building;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "building-ledger",
        url = "${building.base-url:https://apis.data.go.kr/1613000/BldRgstHubService}",
        fallbackFactory = BuildingLedgerFallbackFactory.class)
public interface BuildingLedgerFeignClient {

    @GetMapping("/getBrRecapTitleInfo")
    String fetchRecapTitle(@RequestParam("serviceKey") String serviceKey,
                           @RequestParam("sigunguCd") String sigunguCd,
                           @RequestParam("bjdongCd") String bjdongCd,
                           @RequestParam("platGbCd") String platGbCd,
                           @RequestParam("bun") String bun,
                           @RequestParam("ji") String ji,
                           @RequestParam("_type") String type,
                           @RequestParam("numOfRows") int numOfRows);
}
