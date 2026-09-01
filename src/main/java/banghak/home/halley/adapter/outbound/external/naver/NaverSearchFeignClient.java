package banghak.home.halley.adapter.outbound.external.naver;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 네이버 검색 API — 뉴스 (설계 I137 · I235).
 *
 * <p><b>2026년에 옮겨졌습니다.</b> `openapi.naver.com` 은 같은 키로 <b>401</b> 을 줍니다 —
 * 네이버 클라우드의 <b>API Hub</b> 로 넘어갔고 <b>헤더 이름도 바뀌었습니다.</b>
 *
 * <pre>
 * 옛것  https://openapi.naver.com/v1/search/news.json
 *       X-Naver-Client-Id / X-Naver-Client-Secret          → 401 (실측)
 *
 * 지금  https://naverapihub.apigw.ntruss.com/search/v1/news
 *       X-NCP-APIGW-API-KEY-ID / X-NCP-APIGW-API-KEY       → 200 (실측)
 * </pre>
 *
 * <p><b>응답 구조는 그대로입니다</b>(`items[].title·link·pubDate`). 그래서 파서는
 * 손대지 않았습니다 — 바뀐 것은 <b>주소와 인증뿐</b>입니다.
 *
 * <p>주소가 `.json` 으로 끝나지 않습니다. 형식은 `format` 파라미터로 정하는데
 * 기본이 `json` 이라 <b>안 보냅니다</b> — 기본값을 굳이 적으면 나중에 기본이
 * 바뀌었을 때 알아채지 못합니다.
 */
@FeignClient(name = "naver-search",
        url = "${naver.base-url:https://naverapihub.apigw.ntruss.com/search/v1}",
        fallbackFactory = NaverSearchFallbackFactory.class)
public interface NaverSearchFeignClient {

    /**
     * @param sort `date`(최신순) · `sim`(정확도순). 개발 호재는 <b>최신순</b>이 맞다
     */
    @GetMapping("/news")
    String searchNews(@RequestHeader("X-NCP-APIGW-API-KEY-ID") String clientId,
                      @RequestHeader("X-NCP-APIGW-API-KEY") String clientSecret,
                      @RequestParam("query") String query,
                      @RequestParam("display") int display,
                      @RequestParam("sort") String sort);
}
