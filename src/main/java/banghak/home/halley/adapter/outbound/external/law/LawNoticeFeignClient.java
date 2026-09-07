package banghak.home.halley.adapter.outbound.external.law;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 법제처 국가법령정보 Open API. */
@FeignClient(name = "law-notice",
        url = "${law.base-url:https://www.law.go.kr}",
        fallbackFactory = LawNoticeFallbackFactory.class)
public interface LawNoticeFeignClient {

 /** 고시 목록. 여기서 현행 고시의 행정규칙일련번호를 얻는다. */
    @GetMapping("/DRF/lawSearch.do")
    String search(@RequestParam("OC") String oc,
                  @RequestParam("target") String target,
                  @RequestParam("type") String type,
                  @RequestParam("query") String query);

 /** 고시 본문. 발령일자·공고번호와 첨부 PDF 링크가 여기 있다. */
    @GetMapping("/DRF/lawService.do")
    String detail(@RequestParam("OC") String oc,
                  @RequestParam("target") String target,
                  @RequestParam("type") String type,
                  @RequestParam("ID") String id);

 /** 첨부 PDF 원본. 현황표가 본문이 아니라 여기 있다. */
    @GetMapping("/flDownload.do")
    byte[] download(@RequestParam("flSeq") String flSeq);
}
