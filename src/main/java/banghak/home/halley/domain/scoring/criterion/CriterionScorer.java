package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.Property;

public interface CriterionScorer {

    /** 항목 코드. AUTO/MANUAL/HYBRID 분류는 `criterion` 테이블이 갖는다 (설계 I47). */
    String code();

    ScoreResult score(Property property, ScoringContext ctx);
}
