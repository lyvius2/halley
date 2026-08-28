package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.AgentResponse;
import banghak.home.halley.adapter.inbound.web.dto.CheckLogResponse;
import banghak.home.halley.adapter.inbound.web.dto.CreateDraftRequest;
import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateHistoryResponse;
import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateRequest;
import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateResponse;
import banghak.home.halley.adapter.inbound.web.dto.ParsePreviewRequest;
import banghak.home.halley.adapter.inbound.web.dto.ParsePreviewResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyAgentLink;
import banghak.home.halley.adapter.inbound.web.dto.PropertyAgentResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyImageResponse;
import banghak.home.halley.adapter.inbound.web.dto.ReferenceCardResponse;
import banghak.home.halley.adapter.inbound.web.dto.ScoredPropertyResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateListingStatusRequest;
import banghak.home.halley.adapter.inbound.web.dto.UpdateScoresRequest;
import banghak.home.halley.application.service.AgentService;
import banghak.home.halley.application.service.LoanEstimateService;
import banghak.home.halley.application.service.ParsePreviewService;
import banghak.home.halley.application.service.PropertyImageService;
import banghak.home.halley.application.service.PropertyService;
import banghak.home.halley.application.service.ReferenceTransactionService;
import banghak.home.halley.application.service.ScoringService;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ImageType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;
    private final ScoringService scoringService;
    private final ParsePreviewService parsePreviewService;
    private final LoanEstimateService loanEstimateService;
    private final ReferenceTransactionService referenceTransactionService;
    private final PropertyImageService propertyImageService;
    private final AgentService agentService;

    public PropertyController(PropertyService propertyService,
                              ScoringService scoringService,
                              ParsePreviewService parsePreviewService,
                              LoanEstimateService loanEstimateService,
                              ReferenceTransactionService referenceTransactionService,
                              PropertyImageService propertyImageService,
                              AgentService agentService) {
        this.propertyService = propertyService;
        this.scoringService = scoringService;
        this.parsePreviewService = parsePreviewService;
        this.loanEstimateService = loanEstimateService;
        this.referenceTransactionService = referenceTransactionService;
        this.propertyImageService = propertyImageService;
        this.agentService = agentService;
    }

    @GetMapping("/{id}/agents")
    public List<PropertyAgentResponse> propertyAgents(@PathVariable Long id) {
        return agentService.propertyAgents(id);
    }

    @PutMapping("/{id}/agents")
    public List<PropertyAgentResponse> linkAgents(@PathVariable Long id, @RequestBody List<PropertyAgentLink> links) {
        return agentService.linkAgents(id, links);
    }

    @PostMapping("/{id}/images")
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyImageResponse uploadImage(@PathVariable Long id,
                                             @RequestPart("file") MultipartFile file,
                                             @RequestParam("imageType") ImageType imageType) {
        return propertyImageService.upload(id, file, imageType);
    }

    @GetMapping("/{id}/images")
    public List<PropertyImageResponse> images(@PathVariable Long id) {
        return propertyImageService.list(id);
    }

    @GetMapping("/{id}/reference-transactions")
    public ReferenceCardResponse referenceTransactions(
            @PathVariable Long id,
            @RequestParam(value = "legalDongCode", required = false) String legalDongCode,
            @RequestParam(value = "dealMonth", required = false) String dealMonth) {
        return referenceTransactionService.getReferences(id, legalDongCode, dealMonth);
    }

    @PostMapping("/{id}/loan-estimate")
    public LoanEstimateResponse loanEstimate(@PathVariable Long id, @RequestBody LoanEstimateRequest request) {
        return loanEstimateService.estimate(id, request);
    }

    @GetMapping("/{id}/loan-estimates")
    public List<LoanEstimateHistoryResponse> loanEstimates(@PathVariable Long id) {
        return loanEstimateService.history(id);
    }

    @PostMapping("/parse-preview")
    public ParsePreviewResponse parsePreview(@RequestBody ParsePreviewRequest request) {
        return parsePreviewService.preview(request.text());
    }

    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyResponse createDraft(@RequestBody CreateDraftRequest request) {
        return propertyService.createDraft(request);
    }

    @GetMapping
    public List<ScoredPropertyResponse> list(@RequestParam(value = "dealType", required = false) DealType dealType) {
        return scoringService.list(dealType);
    }

    @GetMapping("/{id}")
    public ScoredPropertyResponse get(@PathVariable Long id) {
        return scoringService.getScored(id);
    }

    @GetMapping("/sold-out/recent")
    public List<PropertyResponse> recentSoldOut() {
        return propertyService.recentSoldOut();
    }

    @GetMapping("/{id}/check-logs")
    public List<CheckLogResponse> checkLogs(@PathVariable Long id) {
        return propertyService.checkLogs(id);
    }

    @PatchMapping("/{id}/status")
    public PropertyResponse updateStatus(@PathVariable Long id, @RequestBody UpdateListingStatusRequest request) {
        return propertyService.updateStatus(id, request.listingStatus());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScoredPropertyResponse create(@RequestBody PropertyRequest request) {
        final PropertyResponse created = propertyService.create(request);
        return scoringService.rescore(created.id());
    }

    @PutMapping("/{id}")
    public ScoredPropertyResponse update(@PathVariable Long id, @RequestBody PropertyRequest request,
                                         @RequestHeader(value = "X-Edit-Version", required = false) Long editVersion) {
        final PropertyResponse updated = propertyService.update(id, request, editVersion);
        return scoringService.rescore(id);
    }

    /**
     * 자동 재채점 트리거 (설계 8장). 매물을 수정하지 않고 점수만 다시 계산한다 —
     * 수집 규칙 버전을 올린 뒤(`PoiDataService.POI_SCHEMA_VERSION`, 설계 I44) POI 재수집을 유도하거나,
     * 외부 API 장애로 폴백된 항목을 복구할 때 쓴다.
     */
    @PostMapping("/{id}/rescore")
    public ScoredPropertyResponse rescore(@PathVariable Long id) {
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
