package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.finance.FinanceCompany;
import banghak.home.halley.domain.finance.FinanceGroup;
import banghak.home.halley.domain.finance.LoanProduct;
import banghak.home.halley.domain.finance.LoanProductType;

import java.util.List;

/** 금융감독원 금융상품통합비교공시 조회. */
public interface FinanceProductPort {

 /** 인증키가 갖춰져 실제로 호출할 수 있는지. */
    boolean isEnabled();

 /** 한 권역의 대출 상품 전부. 페이지는 어댑터가 끝까지 넘긴다. */
    List<LoanProduct> fetchLoanProducts(LoanProductType type, FinanceGroup group);

    List<FinanceCompany> fetchCompanies(FinanceGroup group);
}
