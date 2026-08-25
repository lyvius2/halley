package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.MoveInType;
import banghak.home.halley.domain.scoring.support.PropertyBuilder;
import banghak.home.halley.domain.scoring.support.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MoveInScorerTest {

    private final MoveInScorer scorer = new MoveInScorer();

    @Test
    @DisplayName("즉시입주는 100점, 협의는 85점이다")
    void immediateAndNegotiable() {
        // given
        final ScoringContext ctx = TestContexts.context();

        // when / then
        assertThat(scorer.score(new PropertyBuilder().moveInType(MoveInType.IMMEDIATE).build(), ctx).score())
                .isEqualByComparingTo("100");
        assertThat(scorer.score(new PropertyBuilder().moveInType(MoveInType.NEGOTIABLE).build(), ctx).score())
                .isEqualByComparingTo("85");
    }

    @Test
    @DisplayName("날짜 지정: 당일 100점, 45일 약 80점, 90일 60점, 90일 초과 0점이다")
    void dateBoundaries() {
        // given
        final ScoringContext ctx = TestContexts.context();
        final LocalDate today = TestContexts.REFERENCE_DATE;

        // when / then
        assertThat(scorer.score(new PropertyBuilder().moveInType(MoveInType.DATE).moveInDate(today).build(), ctx).score())
                .isEqualByComparingTo("100");
        assertThat(scorer.score(new PropertyBuilder().moveInType(MoveInType.DATE).moveInDate(today.plusDays(45)).build(), ctx).score())
                .isEqualByComparingTo("80");
        assertThat(scorer.score(new PropertyBuilder().moveInType(MoveInType.DATE).moveInDate(today.plusDays(90)).build(), ctx).score())
                .isEqualByComparingTo("60");
        assertThat(scorer.score(new PropertyBuilder().moveInType(MoveInType.DATE).moveInDate(today.plusDays(91)).build(), ctx).score())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("입주시기나 입주가능일이 없으면 MISSING으로 기록된다")
    void missing() {
        // given
        final ScoringContext ctx = TestContexts.context();

        // when / then
        assertThat(scorer.score(new PropertyBuilder().moveInType(null).build(), ctx).isComputed()).isFalse();
        assertThat(scorer.score(new PropertyBuilder().moveInType(MoveInType.DATE).moveInDate(null).build(), ctx).isComputed())
                .isFalse();
    }
}
