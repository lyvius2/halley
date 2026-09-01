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

    /**
     * <b>항목은 있는데 가중치만 없는 경우</b>를 메운다 (설계 I152).
     *
     * <p>가중치가 없으면 총점 계산에서 <b>그 항목의 무게가 0</b>이 됩니다 —
     * 점수를 아무리 잘 받아도 총점이 꿈쩍하지 않습니다. 그런데 조용합니다:
     * 화면에는 점수가 멀쩡히 뜨고, 총점만 안 움직입니다.
     *
     * <p>실제로 그렇게 됐습니다. `DDL.sql`의 이관 스크립트가 `criterion`에만 넣고
     * `criterion_weight`는 넣지 않았는데, 그다음 기동에서 <b>항목이 이미 있으니
     * 건너뛰어</b> 가중치가 영영 안 생겼습니다.
     *
     * <p>항목 시드와 <b>따로</b> 도는 이유가 그것입니다 — 두 테이블은 같이 움직이지 않습니다.
     */
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
            // 기존 순위 뒤에 붙인다. 앞 항목의 가중치를 흔들면 모든 매물의 총점이 바뀐다
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
