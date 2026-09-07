package banghak.home.halley.adapter.outbound.external.ecos;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** 한국은행 ECOS 통계검색 API. */
@FeignClient(name = "bok-ecos",
        url = "${ecos.base-url:https://ecos.bok.or.kr/api}",
        fallbackFactory = EcosFallbackFactory.class)
public interface EcosFeignClient {


    @GetMapping("/StatisticSearch/{key}/json/kr/{start}/{end}/{statCode}/{cycle}/{from}/{to}")
    String search(@PathVariable("key") String key,
                  @PathVariable("start") int start,
                  @PathVariable("end") int end,
                  @PathVariable("statCode") String statCode,
                  @PathVariable("cycle") String cycle,
                  @PathVariable("from") String from,
                  @PathVariable("to") String to);
}
