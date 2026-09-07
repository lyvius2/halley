package banghak.home.halley.adapter.outbound.external.naver;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/** 네이버 검색 API. 뉴스. */
@FeignClient(name = "naver-search",
        url = "${naver.base-url:https://naverapihub.apigw.ntruss.com/search/v1}",
        fallbackFactory = NaverSearchFallbackFactory.class)
public interface NaverSearchFeignClient {


    @GetMapping("/news")
    String searchNews(@RequestHeader("X-NCP-APIGW-API-KEY-ID") String clientId,
                      @RequestHeader("X-NCP-APIGW-API-KEY") String clientSecret,
                      @RequestParam("query") String query,
                      @RequestParam("display") int display,
                      @RequestParam("sort") String sort);
}
