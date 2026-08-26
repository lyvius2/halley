package banghak.home.halley.config;

import banghak.home.halley.domain.loan.LoanCalculator;
import banghak.home.halley.domain.scoring.criterion.AgeScorer;
import banghak.home.halley.domain.scoring.criterion.BuildingCountScorer;
import banghak.home.halley.domain.scoring.criterion.ComfortScorer;
import banghak.home.halley.domain.scoring.criterion.CriterionScorer;
import banghak.home.halley.domain.scoring.criterion.FloorScorer;
import banghak.home.halley.domain.scoring.criterion.MoveInScorer;
import banghak.home.halley.domain.scoring.criterion.ParkingScorer;
import banghak.home.halley.domain.scoring.criterion.PriceScorer;
import banghak.home.halley.domain.scoring.engine.ScoringEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class ScoringConfig {

    @Bean
    public LoanCalculator loanCalculator() {
        return new LoanCalculator(new BigDecimal("0.4"), 1_000_000_000L);
    }

    @Bean
    public ScoringEngine scoringEngine() {
        return new ScoringEngine();
    }

    @Bean
    public List<CriterionScorer> criterionScorers() {
        return List.of(
                new AgeScorer(),
                new BuildingCountScorer(),
                new ComfortScorer(),
                new FloorScorer(),
                new MoveInScorer(),
                new ParkingScorer(),
                new PriceScorer());
    }
}
