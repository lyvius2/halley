package banghak.home.halley.config;

import banghak.home.halley.domain.itinerary.ItineraryOptimizer;
import banghak.home.halley.domain.scoring.criterion.AgeScorer;
import banghak.home.halley.domain.scoring.criterion.AmenityScorer;
import banghak.home.halley.domain.scoring.criterion.BuildingCountScorer;
import banghak.home.halley.domain.scoring.criterion.ComfortScorer;
import banghak.home.halley.domain.scoring.criterion.CommuteScorer;
import banghak.home.halley.domain.scoring.criterion.CriterionScorer;
import banghak.home.halley.domain.scoring.criterion.EducationScorer;
import banghak.home.halley.domain.scoring.criterion.FloorScorer;
import banghak.home.halley.domain.scoring.criterion.GreenScorer;
import banghak.home.halley.domain.scoring.criterion.MoveInScorer;
import banghak.home.halley.domain.scoring.criterion.ParkingScorer;
import banghak.home.halley.domain.scoring.criterion.PriceScorer;
import banghak.home.halley.domain.scoring.criterion.StationScorer;
import banghak.home.halley.domain.scoring.engine.ScoringEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ScoringConfig {

    @Bean
    public ScoringEngine scoringEngine() {
        return new ScoringEngine();
    }

    @Bean
    public ItineraryOptimizer itineraryOptimizer() {
        return new ItineraryOptimizer();
    }

    @Bean
    public List<CriterionScorer> criterionScorers() {
        return List.of(
                new AgeScorer(),
                new AmenityScorer(),
                new BuildingCountScorer(),
                new CommuteScorer(),
                new ComfortScorer(),
                new EducationScorer(),
                new FloorScorer(),
                new GreenScorer(),
                new MoveInScorer(),
                new ParkingScorer(),
                new PriceScorer(),
                new StationScorer());
    }
}
