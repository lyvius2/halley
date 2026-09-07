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
import java.util.Set;
import java.util.stream.Collectors;

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
            new Seed("HOUSEHOLDS", "세대수", ScoringType.AUTO),
            new Seed("LLM_RECOMMENDATION", "AI 추천도", ScoringType.AUTO),
            new Seed("COMPARATIVE_ADVANTAGE", "비교 우위 추천", ScoringType.AUTO));

    private final CriterionRepository criterionRepository;
    private final CriterionWeightRepository criterionWeightRepository;

    public CriteriaBootstrap(CriterionRepository criterionRepository,
                             CriterionWeightRepository criterionWeightRepository) {
        this.criterionRepository = criterionRepository;
        this.criterionWeightRepository = criterionWeightRepository;
    }

 /** 첫 기동이면 전량 시드하고, 이미 있으면 빠진 항목만 채운다. */
    @Override
    public void run(@NonNull ApplicationArguments args) {
        seedMissingCriteria();
        seedMissingWeights();
    }

    private void seedMissingCriteria() {
        final Set<String> existing = criterionRepository.findAll().stream()
                .map(Criterion::code)
                .collect(Collectors.toSet());
        int added = 0;
        for (int i = 0; i < CRITERIA.size(); i++) {
            final Seed seed = CRITERIA.get(i);
            if (existing.contains(seed.code())) {
                continue;
            }
            final int rank = existing.isEmpty() ? i + 1 : existing.size() + added + 1;
            criterionRepository.save(new Criterion(seed.code(), seed.name(), seed.type(), true));
            criterionWeightRepository.save(new CriterionWeight(
                    seed.code(), rank, WeightCurve.weightFor(rank), null));
            added++;
        }
        if (added > 0) {
            log.info("Seeded {} scoring criteria and their weights.", added);
        }
    }

 /** 항목은 있는데 가중치만 없는 경우를 메운다. */
    private void seedMissingWeights() {
        final Set<String> weighted = criterionWeightRepository.findAll().stream()
                .map(CriterionWeight::criterionCode)
                .collect(Collectors.toSet());
        final List<Criterion> criteria = criterionRepository.findAll();
        int nextRank = weighted.size();
        int added = 0;
        for (final Criterion criterion : criteria) {
            if (weighted.contains(criterion.code())) {
                continue;
            }
            final int rank = ++nextRank;
            criterionWeightRepository.save(new CriterionWeight(
                    criterion.code(), rank, WeightCurve.weightFor(rank), null));
            added++;
            log.warn("Criterion had no weight - total score ignored it. code={}, assignedRank={}",
                    criterion.code(), rank);
        }
        if (added > 0) {
            log.warn("Backfilled {} missing criterion weights. Rescore is needed to reflect them.", added);
        }
    }

    private record Seed(String code, String name, ScoringType type) {
    }
}
