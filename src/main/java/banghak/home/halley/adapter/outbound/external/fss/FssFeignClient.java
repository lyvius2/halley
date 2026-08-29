package banghak.home.halley.adapter.outbound.external.fss;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 금융감독원 금융상품통합비교공시 오픈API (설계 I77).
 *
 * <p>엔드포인트 이름에 응답형식이 붙습니다 — `mortgageLoanProductsSearch.json`.
 * 세 API가 요청 파라미터를 공유하므로 경로만 바꿔 한 메서드로 다룹니다.
 */
@FeignClient(name = "fss-finlife",
        url = "${fss.base-url:http://finlife.fss.or.kr/finlifeapi}",
        fallbackFactory = FssFallbackFactory.class)
public interface FssFeignClient {

    /**
     * @param service `mortgageLoanProductsSearch` · `rentHouseLoanProductsSearch` · `companySearch`
     * @param topFinGrpNo 권역코드. 필수이며 권역마다 따로 불러야 한다
     */
    @GetMapping("/{service}.json")
    String search(@PathVariable("service") String service,
                  @RequestParam("auth") String auth,
                  @RequestParam("topFinGrpNo") String topFinGrpNo,
                  @RequestParam("pageNo") int pageNo);
}
