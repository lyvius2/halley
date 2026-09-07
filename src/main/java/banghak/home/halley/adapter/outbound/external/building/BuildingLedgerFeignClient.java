package banghak.home.halley.adapter.outbound.external.building;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 국토교통부 건축물대장정보 서비스. */
@FeignClient(name = "building-ledger",
        url = "${building.base-url:https://apis.data.go.kr/1613000/BldRgstHubService}",
        fallbackFactory = BuildingLedgerFallbackFactory.class)
public interface BuildingLedgerFeignClient {

 /** 총괄표제부. 단지 전체. 표제부(getBrTitleInfo)는 동마다 한 행이라 */
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
