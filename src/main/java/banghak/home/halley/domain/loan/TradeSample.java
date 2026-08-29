package banghak.home.halley.domain.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 담보가치 추정에 쓰는 실거래 한 건 (설계 I65).
 *
 * @param price        거래금액(원)
 * @param areaM2       전용면적(㎡). 없으면 단가 환산에서 빠진다
 * @param contractDate 계약일. 없으면 최근성 판정에서 오래된 것으로 본다
 */
public record TradeSample(long price, BigDecimal areaM2, LocalDate contractDate) {
}
