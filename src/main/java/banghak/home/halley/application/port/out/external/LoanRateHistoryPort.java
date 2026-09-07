package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.loan.RatePoint;

import java.time.YearMonth;
import java.util.List;

/**
 * 가계대출 금리 시계열.
 *
 * 스트레스 DSR의 기준 금리는 과거 5년 최고 가계대출금리에서 나옵니다. 한 시점의
 * 금리만으로는 구할 수 없어 시계열이 필요하고, 그래서 금감원 공시(`FinanceProductPort`)와
 * 별도의 포트로 둡니다 — 금감원은 지금 파는 상품의 금리를, 여기는 과거 실적을 줍니다.
 *
 * 실패는 예외가 아니라 빈 목록입니다. 못 받으면 사람이 넣어 둔 값을 그대로 씁니다
 * — 금리를 못 받았다고 대출 계산이 멈추면 안 됩니다.
 */
public interface LoanRateHistoryPort {

    /** 설정이 갖춰져 실제로 호출할 수 있는 상태인지. 키가 없으면 false. */
    boolean isEnabled();

    /** 월별 가계대출 금리. 오래된 순서는 보장하지 않으므로 쓰는 쪽에서 정렬한다. */
    List<RatePoint> fetchHouseholdLoanRates(YearMonth from, YearMonth to);
}
