package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.CriterionRepository;
import banghak.home.halley.adapter.outbound.persistence.CriterionWeightRepository;
import banghak.home.halley.domain.scoring.Criterion;
import banghak.home.halley.domain.scoring.CriterionWeight;
import banghak.home.halley.domain.scoring.ScoringType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
@DisplayName("채점 항목 시드 (설계 I152)")
class CriteriaBootstrapTest {

    @Autowired
    private CriteriaBootstrap bootstrap;

    @Autowired
    private CriterionRepository criterionRepository;

    @Autowired
    private CriterionWeightRepository criterionWeightRepository;

    /**
     * 운영에서 실제로 난 상태를 재현한다.
     *
     * <p>이관 스크립트가 `criterion`에만 넣고 `criterion_weight`는 안 넣었다. 그다음 기동에서
     * <b>항목이 이미 있으니 건너뛰어</b> 가중치가 영영 안 생겼고, 총점 계산이 그 항목의
     * 무게를 <b>0으로</b> 봤다 — AI 추천도가 100점이어도 총점은 꿈쩍하지 않는다.
     */
    @Test
    @DisplayName("항목만 있고 가중치가 없으면 채워 넣는다 — 없으면 총점에서 무게가 0이 된다")
    void backfillsWeightWhenOnlyCriterionExists() {
        // given — 가중치 없이 항목만 있다
        criterionRepository.save(new Criterion("ORPHAN_CODE", "가중치 없는 항목", ScoringType.AUTO, true));
        assertThat(criterionWeightRepository.findById("ORPHAN_CODE")).isEmpty();

        // when
        bootstrap.run(new DefaultApplicationArguments());

        // then — 가중치가 생겼고, 0이 아니다
        final CriterionWeight weight = criterionWeightRepository.findById("ORPHAN_CODE").orElseThrow();
        assertThat(weight.weight().doubleValue()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("기존 항목의 가중치는 건드리지 않는다 — 흔들면 모든 매물의 총점이 바뀐다")
    void doesNotTouchExistingWeights() {
        final CriterionWeight before = criterionWeightRepository.findById("PRICE").orElseThrow();

        criterionRepository.save(new Criterion("ORPHAN_CODE", "가중치 없는 항목", ScoringType.AUTO, true));
        bootstrap.run(new DefaultApplicationArguments());

        final CriterionWeight after = criterionWeightRepository.findById("PRICE").orElseThrow();
        assertThat(after.weight()).isEqualByComparingTo(before.weight());
        assertThat(after.priorityRank()).isEqualTo(before.priorityRank());
    }

    @Test
    @DisplayName("모든 채점 항목에 가중치가 있다 — 하나라도 없으면 그 항목은 총점에서 사라진다")
    void everyCriterionHasAWeight() {
        bootstrap.run(new DefaultApplicationArguments());

        final var weighted = criterionWeightRepository.findAll().stream()
                .map(CriterionWeight::criterionCode)
                .toList();
        assertThat(criterionRepository.findAll())
                .allSatisfy(c -> assertThat(weighted)
                        .as("%s 에 가중치가 없다", c.code())
                        .contains(c.code()));
    }
}
