package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CriterionWeightResponse;
import banghak.home.halley.adapter.outbound.persistence.CriterionRepository;
import banghak.home.halley.adapter.outbound.persistence.CriterionWeightRepository;
import banghak.home.halley.config.exception.InvalidWeightsException;
import banghak.home.halley.domain.scoring.Criterion;
import banghak.home.halley.domain.scoring.CriterionWeight;
import banghak.home.halley.domain.scoring.engine.WeightCurve;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CriteriaService {

    private final CriterionRepository criterionRepository;
    private final CriterionWeightRepository criterionWeightRepository;

    public CriteriaService(CriterionRepository criterionRepository,
                           CriterionWeightRepository criterionWeightRepository) {
        this.criterionRepository = criterionRepository;
        this.criterionWeightRepository = criterionWeightRepository;
    }

    public List<CriterionWeightResponse> weights() {
        final Map<String, Criterion> criteria = criterionRepository.findAll().stream()
                .collect(Collectors.toMap(Criterion::code, c -> c));
        return criterionWeightRepository.findAll().stream()
                .sorted(Comparator.comparingInt(CriterionWeight::priorityRank))
                .map(w -> w.createCriterionWeightResponse(criteria))
                .toList();
    }

    @Transactional
    public List<CriterionWeightResponse> updateWeights(List<String> order) {
        final Set<String> knownCodes = criterionRepository.findAll().stream()
                .map(Criterion::code)
                .collect(Collectors.toSet());
        if (order == null || order.size() != knownCodes.size()) {
            throw new InvalidWeightsException("모든 기준의 우선순위 순서가 필요합니다");
        }
        final Set<String> unique = new HashSet<>(order);
        if (unique.size() != order.size()) {
            throw new InvalidWeightsException("중복된 기준이 있습니다");
        }
        if (!knownCodes.containsAll(order)) {
            throw new InvalidWeightsException("알 수 없는 기준이 포함되어 있습니다");
        }

        criterionWeightRepository.deleteAll();
        for (int i = 0; i < order.size(); i++) {
            final int rank = i + 1;
            criterionWeightRepository.save(new CriterionWeight(
                    order.get(i), rank, WeightCurve.weightFor(rank), null));
        }
        return weights();
    }
}
