package banghak.home.halley.adapter.outbound.external.ecos;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 한국은행 ECOS 통계검색 API (설계 I116).
 *
 * <p><b>인증키가 경로에 들어갑니다.</b> 쿼리 파라미터가 아니라 URL 조각이라, 로그에 URL을
 * 통째로 남기면 키가 새어 나갑니다 — 어댑터에서 URL을 로그로 찍지 않습니다.
 *
 * <p>응답은 인증 실패도 HTTP 200으로 돌려주고 본문에 `RESULT.CODE`를 담습니다.
 * 그래서 Feign 예외만으로는 실패를 알 수 없어 본문을 먼저 확인합니다 (V-World와 같은 성질).
 */
@FeignClient(name = "bok-ecos",
        url = "${ecos.base-url:https://ecos.bok.or.kr/api}",
        fallbackFactory = EcosFallbackFactory.class)
public interface EcosFeignClient {

    /**
     * @param cycle `M`(월) · `Q`(분기) · `A`(연)
     * @param from  주기에 맞춘 시작 시점. 월이면 `202401`
     */
    @GetMapping("/StatisticSearch/{key}/json/kr/{start}/{end}/{statCode}/{cycle}/{from}/{to}")
    String search(@PathVariable("key") String key,
                  @PathVariable("start") int start,
                  @PathVariable("end") int end,
                  @PathVariable("statCode") String statCode,
                  @PathVariable("cycle") String cycle,
                  @PathVariable("from") String from,
                  @PathVariable("to") String to);
}
