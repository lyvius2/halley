package banghak.home.halley.domain.itinerary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ItineraryOptimizerTest {

    private final ItineraryOptimizer optimizer = new ItineraryOptimizer();

    @Test
    @DisplayName("출발지에서 모든 노드를 거치는 최소 총 이동시간 순서를 찾는다")
    void findsMinimumOrder() {
        // given — start(0) → B가 1분으로 가장 가까움
        final TravelCostMatrix matrix = (from, to) -> Map.of(
                -1L + "," + 10L, 5,
                -1L + "," + 20L, 1,
                10L + "," + 20L, 2,
                20L + "," + 10L, 5).getOrDefault(from + "," + to, 99);

        // when
        final List<Long> order = optimizer.optimize(-1L, List.of(10L, 20L), matrix);

        // then — start→20(1) →10(2) = 3 < start→10(5)→20(2)=7
        assertThat(order).containsExactly(20L, 10L);
    }

    @Test
    @DisplayName("노드가 없으면 빈 순서를 반환한다")
    void emptyNodes() {
        // when
        final List<Long> order = optimizer.optimize(-1L, List.of(), (a, b) -> 0);

        // then
        assertThat(order).isEmpty();
    }

    @Test
    @DisplayName("3개 노드에서도 최소 순서를 찾는다")
    void findsMinimumAmongThree() {
        // given — A→B→C가 최소 (각 1분), A→C는 9분
        final TravelCostMatrix matrix = (from, to) -> {
            if (from == -1L) {
                return 0;
            }
            return (from == 1L && to == 2L) || (from == 2L && to == 3L) || (from == 1L && to == 3L) ? 1 : 9;
        };

        // when
        final List<Long> order = optimizer.optimize(-1L, List.of(1L, 2L, 3L), matrix);

        // then
        assertThat(order).containsExactly(1L, 2L, 3L);
    }
}
