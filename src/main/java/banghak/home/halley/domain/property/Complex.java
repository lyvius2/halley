package banghak.home.halley.domain.property;

import java.math.BigDecimal;
import java.time.Instant;

public record Complex(
        Long id,
        /** {@link ComplexKey} 가 만든 값. 이것이 같으면 같은 단지다  */
        String matchKey,
        String name,
        String addressJibun,
        BigDecimal lat,
        BigDecimal lng,
        Instant createdAt
) {
}
