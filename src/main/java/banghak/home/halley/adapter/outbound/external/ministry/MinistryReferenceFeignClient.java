package banghak.home.halley.adapter.outbound.external.ministry;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ministry-reference",
        url = "${ministry.base-url:https://apis.data.go.kr/1613000}",
        fallbackFactory = MinistryReferenceFallbackFactory.class)
public interface MinistryReferenceFeignClient {

    /**
     * 한 달치 아파트 매매 (설계 I219).
     *
     * <p><b>`numOfRows` 를 반드시 줍니다.</b> 안 주면 국토부가 <b>10건만</b> 돌려줍니다 —
     * 성북구 2025년 6월은 실제로 <b>660건</b>인데 10건만 보고 있었습니다.
     * 특정 단지를 찾는 일이라, 앞에서 10건 자르면 <b>거의 못 찾습니다.</b>
     */
    @GetMapping("/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev")
    String fetchTrade(@RequestParam("serviceKey") String serviceKey,
                      @RequestParam("LAWD_CD") String lawdCd,
                      @RequestParam("DEAL_YMD") String dealYmd,
                      @RequestParam("numOfRows") int numOfRows);

    /** 전월세 (설계 I131). 같은 서비스의 다른 오퍼레이션이라 <b>인증키를 재사용</b>한다. */
    @GetMapping("/RTMSDataSvcAptRent/getRTMSDataSvcAptRent")
    String fetchRent(@RequestParam("serviceKey") String serviceKey,
                     @RequestParam("LAWD_CD") String lawdCd,
                     @RequestParam("DEAL_YMD") String dealYmd,
                     @RequestParam("numOfRows") int numOfRows);
}
