package banghak.home.halley.adapter.outbound.external.kakao;

import banghak.home.halley.config.KakaoFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "kakao-local",
        url = "${kakao.local.base-url:https://dapi.kakao.com}",
        configuration = KakaoFeignConfig.class,
        fallbackFactory = KakaoLocalFeignFallbackFactory.class)
public interface KakaoLocalFeignClient {

    @GetMapping("/v2/local/search/address.json")
    String searchAddress(@RequestParam("query") String query);

    @GetMapping("/v2/local/search/category.json")
    String searchCategory(@RequestParam("category_group_code") String categoryGroupCode,
                          @RequestParam("x") String x,
                          @RequestParam("y") String y,
                          @RequestParam("radius") int radius);

    @GetMapping("/v2/local/search/keyword.json")
    String searchKeyword(@RequestParam("query") String query,
                         @RequestParam("category_group_code") String categoryGroupCode,
                         @RequestParam("x") String x,
                         @RequestParam("y") String y,
                         @RequestParam("radius") int radius,
                         @RequestParam("sort") String sort);
}
