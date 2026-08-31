package banghak.home.halley.adapter.outbound.external.naver;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 네이버 검색 API — 뉴스 (설계 I137).
 *
 * <p>인증이 <b>헤더</b>입니다. 다른 연동들과 달리 쿼리 파라미터가 아닙니다.
 */
@FeignClient(name = "naver-search",
        url = "${naver.base-url:https://openapi.naver.com/v1/search}",
        fallbackFactory = NaverSearchFallbackFactory.class)
public interface NaverSearchFeignClient {

    /**
     * @param sort `date`(최신순) · `sim`(정확도순). 개발 호재는 <b>최신순</b>이 맞다
     */
    @GetMapping("/news.json")
    String searchNews(@RequestHeader("X-Naver-Client-Id") String clientId,
                      @RequestHeader("X-Naver-Client-Secret") String clientSecret,
                      @RequestParam("query") String query,
                      @RequestParam("display") int display,
                      @RequestParam("sort") String sort);
}
