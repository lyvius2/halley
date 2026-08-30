package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.AgentResponse;
import banghak.home.halley.adapter.inbound.web.dto.CheckLogResponse;
import banghak.home.halley.adapter.inbound.web.dto.CommentRequest;
import banghak.home.halley.adapter.inbound.web.dto.ComparativeAnalysisStatus;
import banghak.home.halley.adapter.inbound.web.dto.CommentResponse;
import banghak.home.halley.adapter.inbound.web.dto.CreateDraftRequest;
import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateHistoryResponse;
import banghak.home.halley.adapter.inbound.web.dto.LandUseResponse;
import banghak.home.halley.adapter.inbound.web.dto.LlmRecommendationResponse;
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
import banghak.home.halley.adapter.inbound.web.dto.ScoreVersionResponse;
import banghak.home.halley.adapter.inbound.web.dto.ScoredPropertyResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateListingStatusRequest;
import banghak.home.halley.adapter.inbound.web.dto.UpdateScoresRequest;
import banghak.home.halley.application.service.AgentService;
import banghak.home.halley.application.service.ComparativeAnalysisService;
import banghak.home.halley.application.service.LandUseService;
import banghak.home.halley.application.service.PropertyEnrichmentService;
import banghak.home.halley.application.service.LlmRecommendationService;
import banghak.home.halley.application.service.PropertyCommentService;
import banghak.home.halley.application.service.LoanEstimateService;
import banghak.home.halley.application.service.ParsePreviewService;
import banghak.home.halley.application.service.PropertyImageService;
import banghak.home.halley.application.service.PropertyService;
import banghak.home.halley.application.service.ReferenceTransactionService;
import banghak.home.halley.application.service.ScoringService;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ImageType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final PropertyCommentService propertyCommentService;
    private final LlmRecommendationService llmRecommendationService;
    private final ComparativeAnalysisService comparativeAnalysisService;
    private final LandUseService landUseService;
    private final PropertyEnrichmentService propertyEnrichmentService;

    public PropertyController(PropertyService propertyService,
                              ScoringService scoringService,
                              ParsePreviewService parsePreviewService,
                              LoanEstimateService loanEstimateService,
                              ReferenceTransactionService referenceTransactionService,
                              PropertyImageService propertyImageService,
                              AgentService agentService,
                              PropertyCommentService propertyCommentService,
                              LlmRecommendationService llmRecommendationService,
                              ComparativeAnalysisService comparativeAnalysisService,
                              LandUseService landUseService,
                              PropertyEnrichmentService propertyEnrichmentService) {
        this.propertyService = propertyService;
        this.scoringService = scoringService;
        this.parsePreviewService = parsePreviewService;
        this.loanEstimateService = loanEstimateService;
        this.referenceTransactionService = referenceTransactionService;
        this.propertyImageService = propertyImageService;
        this.agentService = agentService;
        this.propertyCommentService = propertyCommentService;
        this.llmRecommendationService = llmRecommendationService;
        this.comparativeAnalysisService = comparativeAnalysisService;
        this.landUseService = landUseService;
        this.propertyEnrichmentService = propertyEnrichmentService;
    }

    /** 비교 우위 분석 현황 — 실행 가능 여부와 저장된 순위 (설계 I61). */
    @GetMapping("/comparative-analysis")
    public ComparativeAnalysisStatus comparativeAnalysis() {
        return comparativeAnalysisService.status();
    }

    /** 등록된 매물 전체를 견주어 순위를 매긴다. 매물이 4개 미만이면 409. */
    @PostMapping("/comparative-analysis")
    public ComparativeAnalysisStatus runComparativeAnalysis() {
        comparativeAnalysisService.analyse();
        return comparativeAnalysisService.status();
    }

    @GetMapping("/{id}/land-use")
    public List<LandUseResponse> landUse(@PathVariable Long id) {
        return landUseService.find(id);
    }

    /** 토지이용계획을 다시 받아 온다. 거의 바뀌지 않아 평소에는 저장값을 쓴다. */
    @PostMapping("/{id}/land-use")
    public List<LandUseResponse> refreshLandUse(@PathVariable Long id) {
        return landUseService.refresh(id);
    }

    /**
     * AI 추천도. 화면이 2초 간격으로 폴링하므로 <b>결과가 없어도 200</b>을 돌려주고
     * `pending`으로 '분석 중'과 '미산출'을 가른다 (설계 I72).
     */
    @GetMapping("/{id}/llm-recommendation")
    public LlmRecommendationResponse llmRecommendation(@PathVariable Long id) {
        return llmRecommendationService.find(id)
                .map(LlmRecommendationResponse::from)
                .orElseGet(() -> LlmRecommendationResponse.empty(id, llmRecommendationService.isRunning(id)));
    }

    @GetMapping("/{id}/comments")
    public List<CommentResponse> comments(@PathVariable Long id) {
        return propertyCommentService.list(id);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable Long id, @RequestBody CommentRequest request) {
        return propertyCommentService.create(id, request);
    }

    @PutMapping("/{id}/comments/{commentId}")
    public CommentResponse editComment(@PathVariable Long id,
                                       @PathVariable Long commentId,
                                       @RequestBody CommentRequest request) {
        return propertyCommentService.update(id, commentId, request);
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeComment(@PathVariable Long id, @PathVariable Long commentId) {
        propertyCommentService.delete(id, commentId);
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

    @DeleteMapping("/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        propertyImageService.delete(id, imageId);
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
        // 초등학교·토지이용계획·채점까지 기다렸다가 돌려준다 (설계 I110). 화면은 그동안
        // 진행 표시를 띄운다. 공시가격·실거래가·AI 추천도는 배경으로 넘어간다
        propertyEnrichmentService.enrich(created.id());
        return scoringService.getScored(created.id());
    }

    @PutMapping("/{id}")
    public ScoredPropertyResponse update(@PathVariable Long id, @RequestBody PropertyRequest request,
                                         @RequestHeader(value = "X-Edit-Version", required = false) Long editVersion) {
        final PropertyResponse updated = propertyService.update(id, request, editVersion);
        return scoringService.rescore(id);
    }

    /**
     * 채점 판 번호 목록 (설계 I85).
     *
     * <p>채점은 <b>사용자가 보고 있는 동안 뒤에서 바뀝니다</b> — 보정이 끝나고, AI 응답이 옵니다.
     * 화면이 그걸 알아채려고 목록을 통째로 다시 받으면 무겁습니다. 이 번호만 확인하고
     * 달라진 게 있을 때만 목록을 받습니다.
     */
    @GetMapping("/score-versions")
    public List<ScoreVersionResponse> scoreVersions() {
        return scoringService.scoreVersions();
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
