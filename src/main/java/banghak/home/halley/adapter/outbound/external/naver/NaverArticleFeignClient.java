package banghak.home.halley.adapter.outbound.external.naver;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "naver-checker",
        url = "${naver.checker.base-url:https://fin.land.naver.com}",
        fallbackFactory = NaverArticleFallbackFactory.class)
public interface NaverArticleFeignClient {

    @GetMapping("/articles/{articleNo}")
    String fetch(@PathVariable("articleNo") String articleNo);
}
