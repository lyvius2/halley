package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.scoring.support.PropertyBuilder;
import banghak.home.halley.domain.scoring.support.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceScorerTest {

    private final PriceScorer scorer = new PriceScorer();

    @Test
    @DisplayName("예산상한(현금+대출한도) 대비 호가 비율로 감점한다")
    void priceRelativeToBudget() {
        // given
        // 현금 5억 + 호가 4억×LTV 0.4 = 1.6억 → 예산상한 6.6억
        final ScoringContext ctx = TestContexts.context(500_000_000L, List.of());

        // when / then
        assertThat(scorer.score(new PropertyBuilder().priceDeposit(400_000_000L).build(), ctx).score())
                .isEqualByComparingTo("39.39");
        assertThat(scorer.score(new PropertyBuilder().priceDeposit(800_000_000L).build(), ctx).score())
                .isEqualByComparingTo("2.44");
    }

    @Test
    @DisplayName("호가가 예산상한을 초과하면 0점으로 클램프된다")
    void overBudgetClampsToZero() {
        // given
        final ScoringContext ctx = TestContexts.context(0L, List.of());

        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().priceDeposit(100_000_000L).build(), ctx);

        // then
        assertThat(result.score()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("호가가 없으면 MISSING으로 기록된다")
    void missing() {
        // given
        final ScoringContext ctx = TestContexts.context();

        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().priceDeposit(null).build(), ctx);

        // then
        assertThat(result.isComputed()).isFalse();
        assertThat(result.fallbackReason()).isEqualTo("호가 없음");
    }
}
