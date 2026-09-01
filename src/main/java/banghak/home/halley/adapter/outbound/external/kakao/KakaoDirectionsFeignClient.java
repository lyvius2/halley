package banghak.home.halley.adapter.outbound.external.kakao;

import banghak.home.halley.config.KakaoFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "kakao-directions",
        url = "${kakao.directions.base-url:https://apis-navi.kakaomobility.com}",
        configuration = KakaoFeignConfig.class,
        fallbackFactory = KakaoDirectionsFallbackFactory.class)
public interface KakaoDirectionsFeignClient {

    @GetMapping("/v1/directions")
    String directions(@RequestParam("origin") String origin,
                      @RequestParam("destination") String destination,
                      @RequestParam("priority") String priority);

    /**
     * 그 날 그 시각의 길 (설계 I196).
     *
     * <p>`/v1/directions` 는 <b>지금</b>의 길을 줍니다. 임장은 대개 다음 주말입니다 —
     * 화요일 저녁에 물어본 소요시간으로 일요일 낮의 계획을 세우면 어긋납니다.
     *
     * <p>실제로 갈라집니다. 신림→강남을 화요일 19시로 물으면 31분,
     * 일요일 14시로 물으면 24분입니다.
     *
     * @param departureTime `yyyyMMddHHmm`
     */
    @GetMapping("/v1/future/directions")
    String futureDirections(@RequestParam("origin") String origin,
                            @RequestParam("destination") String destination,
                            @RequestParam("priority") String priority,
                            @RequestParam("departure_time") String departureTime);
}
