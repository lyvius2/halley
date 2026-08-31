package banghak.home.halley.domain.forecast;

import java.math.BigDecimal;

/**
 * 한 구간의 실거래 통계 (설계 I130).
 *
 * @param median <b>평균이 아니라 중앙값입니다.</b> 표본이 얇아 대형 평형 한 건이 섞이면
 *               평균은 통째로 끌려갑니다
 * @param count  표본 수. <b>이 값을 화면에 함께 보여 줍니다</b> — 3건으로 낸 판단과
 *               30건으로 낸 판단은 다르고, 사용자가 그것을 알아야 합니다
 */
public record TradeStat(BigDecimal median, int count) {

    public boolean isEmpty() {
        return count == 0 || median == null;
    }
}
