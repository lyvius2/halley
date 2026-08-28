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

    /**
     * 첫 기동이면 전량 시드하고, 이미 있으면 <b>빠진 항목만</b> 채운다.
     * 항목이 추가돼도(예: I59의 AI 추천도) 기존 설치에서 화면에 뜨지 않는 일이 없도록 한다.
     */
    @Override
    public void run(@NonNull ApplicationArguments args) {
        final Set<String> existing = criterionRepository.findAll().stream()
                .map(Criterion::code)
                .collect(Collectors.toSet());
        int added = 0;
        for (int i = 0; i < CRITERIA.size(); i++) {
            final Seed seed = CRITERIA.get(i);
            if (existing.contains(seed.code())) {
                continue;
            }
            // 나중에 추가된 항목은 기존 순위 뒤에 붙인다 — 앞 항목의 가중치를 흔들지 않는다
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

    private record Seed(String code, String name, ScoringType type) {
    }
}
