package banghak.home.halley.domain.finance;

import java.util.List;

/**
 * 금감원에 공시된 금융회사 (설계 I77).
 *
 * <p>대출 상품 응답에는 회사명(`kor_co_nm`)만 있고 홈페이지·콜센터가 없습니다. 상품에서
 * 회사로 넘어가려면 이 목록이 필요합니다.
 *
 * @param areas 영업 지역명. 저축은행처럼 지역이 한정된 곳을 거르는 데 쓴다
 */
public record FinanceCompany(
        FinanceGroup group,
        String dclsMonth,
        String finCoNo,
        String name,
        String homepageUrl,
        String callCenterTel,
        List<String> areas
) {

    public FinanceCompany {
        areas = areas == null ? List.of() : List.copyOf(areas);
    }
}
