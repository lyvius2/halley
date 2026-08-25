package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.ScoringType;

public interface CriterionScorer {

    String code();

    ScoringType type();

    ScoreResult score(Property property, ScoringContext ctx);
}
