package banghak.home.halley.adapter.outbound.external.fss;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "fss-finlife",
        url = "${fss.base-url:https://finlife.fss.or.kr/finlifeapi}",
        fallbackFactory = FssFallbackFactory.class)
public interface FssFeignClient {

    @GetMapping("/{service}.json")
    String search(@PathVariable("service") String service,
                  @RequestParam("auth") String auth,
                  @RequestParam("topFinGrpNo") String topFinGrpNo,
                  @RequestParam("pageNo") int pageNo);
}
