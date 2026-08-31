package banghak.home.halley.adapter.outbound.external.building;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 국토교통부 건축물대장정보 서비스 (설계 I132).
 *
 * <p>실거래가와 <b>같은 기관·같은 인증키</b>지만 <b>다른 서비스</b>라 따로 활용신청이 필요합니다.
 *
 * <p><b>실거래가와 달리 JSON을 줍니다</b>(`_type=json`). 실거래가는 XML이라 어댑터가
 * DOM으로 읽는데, 여기는 Jackson으로 읽습니다.
 */
@FeignClient(name = "building-ledger",
        url = "${building.base-url:https://apis.data.go.kr/1613000/BldRgstHubService}",
        fallbackFactory = BuildingLedgerFallbackFactory.class)
public interface BuildingLedgerFeignClient {

    /**
     * 총괄표제부 — <b>단지 전체</b>. 표제부(`getBrTitleInfo`)는 동마다 한 행이라
     * 대지면적이 중복됩니다.
     *
     * @param platGbCd <b>PNU와 코드 체계가 다릅니다.</b> PNU는 `1`=대지·`2`=산인데
     *                 여기는 `0`=대지·`1`=산·`2`=블록입니다
     */
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
