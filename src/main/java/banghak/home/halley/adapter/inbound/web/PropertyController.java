package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.inbound.web.dto.ScoredPropertyResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateScoresRequest;
import banghak.home.halley.application.service.PropertyService;
import banghak.home.halley.application.service.ScoringService;
import banghak.home.halley.domain.property.DealType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;
    private final ScoringService scoringService;

    public PropertyController(PropertyService propertyService, ScoringService scoringService) {
        this.propertyService = propertyService;
        this.scoringService = scoringService;
    }

    @GetMapping
    public List<ScoredPropertyResponse> list(@RequestParam(value = "dealType", required = false) DealType dealType) {
        return scoringService.list(dealType);
    }

    @GetMapping("/{id}")
    public ScoredPropertyResponse get(@PathVariable Long id) {
        return scoringService.getScored(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScoredPropertyResponse create(@RequestBody PropertyRequest request) {
        final PropertyResponse created = propertyService.create(request);
        return scoringService.rescore(created.id());
    }

    @PutMapping("/{id}")
    public ScoredPropertyResponse update(@PathVariable Long id, @RequestBody PropertyRequest request) {
        final PropertyResponse updated = propertyService.update(id, request);
        return scoringService.rescore(id);
    }

    @PutMapping("/{id}/scores")
    public ScoredPropertyResponse updateScores(@PathVariable Long id, @RequestBody UpdateScoresRequest request) {
        return scoringService.saveManualScores(id, request.scores());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        propertyService.delete(id);
    }
}
