package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.finance.FinanceCompany;
import banghak.home.halley.domain.finance.FinanceGroup;
import banghak.home.halley.domain.finance.LoanProduct;
import banghak.home.halley.domain.finance.LoanProductType;

import java.util.List;

/**
 * 금융감독원 금융상품통합비교공시 조회 (설계 I77).
 *
 * <p>지금까지 대출 금리는 관리 화면에서 손으로 넣은 값 하나였습니다(`RegulationParam`).
 * 실제로는 회사·담보유형·상환방식·금리유형마다 다르고 매달 바뀝니다.
 *
 * <p>실패는 예외가 아니라 빈 목록으로 돌려줍니다. 금리는 보조 입력이라 못 받아도 나머지
 * 계산은 그대로 나와야 합니다 (설계 12.2 원칙).
 */
public interface FinanceProductPort {

    /** 인증키가 갖춰져 실제로 호출할 수 있는지. */
    boolean isEnabled();

    /** 한 권역의 대출 상품 전부. 페이지는 어댑터가 끝까지 넘긴다. */
    List<LoanProduct> fetchLoanProducts(LoanProductType type, FinanceGroup group);

    List<FinanceCompany> fetchCompanies(FinanceGroup group);
}
