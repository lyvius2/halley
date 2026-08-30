package banghak.home.halley.application.port.out.cache;

import banghak.home.halley.domain.finance.LoanProductType;
import banghak.home.halley.domain.finance.MarketRate;

import java.util.Optional;

/**
 * 금감원 공시 금리 캐시 (설계 I81).
 *
 * <p><b>공시는 월 단위로만 바뀝니다.</b> 매 계산마다 부르면 같은 답을 반복해서 받는 낭비이고,
 * 무엇보다 <b>일 허용횟수가 있는 API</b>입니다(`err_cd = 020`). 대출 계산은 매물마다 도는
 * 흔한 동작이라 캐시 없이는 금방 한도에 걸립니다.
 *
 * <p>미스가 나면 호출 측이 규제 파라미터의 기본 금리로 떨어집니다 — 캐시가 죽어도 대출 계산
 * 자체는 계속 돌아야 합니다(설계 12.2 원칙).
 */
public interface MarketRateCache {

    Optional<MarketRate> get(LoanProductType type);

    void put(MarketRate rate);
}
