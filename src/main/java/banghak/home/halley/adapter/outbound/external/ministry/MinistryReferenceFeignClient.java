package banghak.home.halley.adapter.outbound.external.ministry;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ministry-reference",
        url = "${ministry.base-url:https://apis.data.go.kr/1613000}",
        fallbackFactory = MinistryReferenceFallbackFactory.class)
public interface MinistryReferenceFeignClient {

    @GetMapping("/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev")
    String fetchTrade(@RequestParam("serviceKey") String serviceKey,
                      @RequestParam("LAWD_CD") String lawdCd,
                      @RequestParam("DEAL_YMD") String dealYmd);

    /** 전월세 (설계 I131). 같은 서비스의 다른 오퍼레이션이라 <b>인증키를 재사용</b>한다. */
    @GetMapping("/RTMSDataSvcAptRent/getRTMSDataSvcAptRent")
    String fetchRent(@RequestParam("serviceKey") String serviceKey,
                     @RequestParam("LAWD_CD") String lawdCd,
                     @RequestParam("DEAL_YMD") String dealYmd);
}
