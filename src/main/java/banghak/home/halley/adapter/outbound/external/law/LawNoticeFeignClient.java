package banghak.home.halley.adapter.outbound.external.law;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 법제처 국가법령정보 Open API (설계 I73).
 *
 * <p>{@code OC}는 인증키가 아니라 <b>이메일 ID</b>입니다(`hong@korea.kr` → `hong`). 시험용
 * {@code test}로도 응답하지만 언제까지 열려 있을지 보장이 없어 운영에서는 발급받아 씁니다.
 *
 * <p>규제지역 고시는 법률이 아니라 <b>행정규칙</b>이라 {@code target=admrul}입니다.
 */
@FeignClient(name = "law-notice",
        url = "${law.base-url:http://www.law.go.kr}",
        fallbackFactory = LawNoticeFallbackFactory.class)
public interface LawNoticeFeignClient {

    /** 고시 목록 — 여기서 현행 고시의 `행정규칙일련번호`를 얻는다. */
    @GetMapping("/DRF/lawSearch.do")
    String search(@RequestParam("OC") String oc,
                  @RequestParam("target") String target,
                  @RequestParam("type") String type,
                  @RequestParam("query") String query);

    /** 고시 본문 — 발령일자·공고번호와 첨부 PDF 링크가 여기 있다. */
    @GetMapping("/DRF/lawService.do")
    String detail(@RequestParam("OC") String oc,
                  @RequestParam("target") String target,
                  @RequestParam("type") String type,
                  @RequestParam("ID") String id);

    /** 첨부 PDF 원본. 현황표가 본문이 아니라 여기 있다. */
    @GetMapping("/flDownload.do")
    byte[] download(@RequestParam("flSeq") String flSeq);
}
