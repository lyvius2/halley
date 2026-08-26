package banghak.home.halley.adapter.outbound.external.kakao;

import banghak.home.halley.config.KakaoFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "kakao-directions",
        url = "${kakao.directions.base-url:https://apis-navi.kakamobility.com}",
        configuration = KakaoFeignConfig.class,
        fallbackFactory = KakaoDirectionsFallbackFactory.class)
public interface KakaoDirectionsFeignClient {

    @GetMapping("/v1/directions")
    String directions(@RequestParam("origin") String origin,
                      @RequestParam("destination") String destination,
                      @RequestParam("priority") String priority);
}
