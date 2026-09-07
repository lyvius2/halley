package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.loan.RatePoint;

import java.time.YearMonth;
import java.util.List;

/** 가계대출 금리 시계열. */
public interface LoanRateHistoryPort {

 /** 설정이 갖춰져 실제로 호출할 수 있는 상태인지. 키가 없으면 false. */
    boolean isEnabled();

 /** 월별 가계대출 금리. 오래된 순서는 보장하지 않으므로 쓰는 쪽에서 정렬한다. */
    List<RatePoint> fetchHouseholdLoanRates(YearMonth from, YearMonth to);
}
