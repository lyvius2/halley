package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.CriterionWeightResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateWeightsRequest;
import banghak.home.halley.application.service.CriteriaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/criteria")
public class CriteriaController {

    private final CriteriaService criteriaService;

    public CriteriaController(CriteriaService criteriaService) {
        this.criteriaService = criteriaService;
    }

    @GetMapping("/weights")
    public List<CriterionWeightResponse> weights() {
        return criteriaService.weights();
    }

    @PutMapping("/weights")
    public List<CriterionWeightResponse> updateWeights(@RequestBody UpdateWeightsRequest request) {
        return criteriaService.updateWeights(request.order());
    }
}
