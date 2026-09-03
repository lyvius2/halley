package banghak.home.halley.domain.property;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 단지 하나 (설계 I266) — 매물이 여기에 딸린다(단지 1 : 매물 N). 그룹으로 가르지 않는다,
 * 격리는 여전히 {@code property.group_id} 가 한다 (설계 I87).
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
