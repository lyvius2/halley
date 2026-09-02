package banghak.home.halley.domain.property;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 단지 하나 (설계 I266).
 *
 * <p>매물이 여기에 딸립니다 — <b>단지 1 : 매물 N</b>. 국토부 실거래는 매물이
 * 아니라 <b>단지와 평형</b>에 붙습니다.
 *
 * <p><b>그룹으로 가르지 않습니다.</b> 아파트 단지는 공개 정보이고, 국토부 실거래도
 * 마찬가지입니다. 격리는 여전히 {@code property.group_id} 가 합니다 (설계 I87) —
 * 사람은 늘 <b>자기 매물</b>을 통해서만 단지에 닿습니다.
 */
public record Complex(
        Long id,
        /** {@link ComplexKey} 가 만든 값. 이것이 같으면 같은 단지다 */
        String matchKey,
        String name,
        String addressJibun,
        BigDecimal lat,
        BigDecimal lng,
        Instant createdAt
) {
}
