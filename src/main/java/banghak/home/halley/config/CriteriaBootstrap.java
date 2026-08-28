package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.CriterionRepository;
import banghak.home.halley.adapter.outbound.persistence.CriterionWeightRepository;
import banghak.home.halley.domain.scoring.Criterion;
import banghak.home.halley.domain.scoring.CriterionWeight;
import banghak.home.halley.domain.scoring.ScoringType;
import banghak.home.halley.domain.scoring.engine.WeightCurve;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class CriteriaBootstrap implements ApplicationRunner {

    private static final List<Seed> CRITERIA = List.of(
            new Seed("COMFORT", "공간의 쾌적함", ScoringType.MANUAL),
            new Seed("PRICE", "가격", ScoringType.AUTO),
            new Seed("MOVE_IN", "입주시기", ScoringType.AUTO),
            new Seed("COMMUTE", "직주근접", ScoringType.AUTO),
            new Seed("AGE", "건물 연식", ScoringType.AUTO),
            new Seed("FLOOR", "층", ScoringType.AUTO),
            new Seed("STATION", "역세권", ScoringType.AUTO),
            new Seed("EDUCATION", "교육여건", ScoringType.HYBRID),
            new Seed("AMENITY", "편의시설", ScoringType.AUTO),
            new Seed("PARKING", "주차", ScoringType.AUTO),
            new Seed("GREEN", "녹색환경", ScoringType.HYBRID),
            new Seed("HOUSEHOLDS", "세대수", ScoringType.AUTO));

    private final CriterionRepository criterionRepository;
    private final CriterionWeightRepository criterionWeightRepository;

    public CriteriaBootstrap(CriterionRepository criterionRepository,
                             CriterionWeightRepository criterionWeightRepository) {
        this.criterionRepository = criterionRepository;
        this.criterionWeightRepository = criterionWeightRepository;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (!criterionRepository.findAll().isEmpty()) {
            return;
        }
        for (int i = 0; i < CRITERIA.size(); i++) {
            final Seed seed = CRITERIA.get(i);
            final int rank = i + 1;
            criterionRepository.save(new Criterion(seed.code(), seed.name(), seed.type(), true));
            criterionWeightRepository.save(new CriterionWeight(
                    seed.code(), rank, WeightCurve.weightFor(rank), null));
        }
        log.info("Seeded {} scoring criteria and their weights.", CRITERIA.size());
    }

    private record Seed(String code, String name, ScoringType type) {
    }
}
