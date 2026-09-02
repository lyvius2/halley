package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.CreateRegulationProfileRequest;
import banghak.home.halley.adapter.inbound.web.dto.LlmModelSettingsResponse;
import banghak.home.halley.adapter.inbound.web.dto.NotificationLogResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateLlmModelRequest;
import banghak.home.halley.application.service.LlmModelService;
import banghak.home.halley.adapter.inbound.web.dto.NotificationSettingsResponse;
import banghak.home.halley.adapter.inbound.web.dto.RegulatedAreaRequest;
import banghak.home.halley.adapter.inbound.web.dto.RegulatedAreaResponse;
import banghak.home.halley.adapter.inbound.web.dto.RegulationProfileResponse;
import banghak.home.halley.adapter.inbound.web.dto.SystemConfigResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateConfigRequest;
import banghak.home.halley.adapter.inbound.web.dto.UpdateRegulationParamRequest;
import banghak.home.halley.application.service.NotificationService;
import banghak.home.halley.application.service.RegulationAdminService;
import banghak.home.halley.application.service.SystemConfigService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import banghak.home.halley.adapter.inbound.web.dto.GroupRenameRequest;
import banghak.home.halley.adapter.inbound.web.dto.GroupResponse;
import banghak.home.halley.application.service.GroupService;
import banghak.home.halley.application.service.StressRateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/admin")
public class AdminSettingsController {

    private final SystemConfigService systemConfigService;
    private final NotificationService notificationService;
    private final RegulationAdminService regulationAdminService;
    private final GroupService groupService;
    private final StressRateService stressRateService;
    private final LlmModelService llmModelService;

    public AdminSettingsController(SystemConfigService systemConfigService,
                                   NotificationService notificationService,
                                   RegulationAdminService regulationAdminService,
                                   GroupService groupService,
                                   StressRateService stressRateService,
                                   LlmModelService llmModelService) {
        this.systemConfigService = systemConfigService;
        this.notificationService = notificationService;
        this.regulationAdminService = regulationAdminService;
        this.groupService = groupService;
        this.stressRateService = stressRateService;
        this.llmModelService = llmModelService;
    }

    /**
     * AI 모델 설정 (설계 I267).
     *
     * <p>자리 넷과 고를 수 있는 모델을 <b>한 번에</b> 보냅니다 — 화면이 두 번
     * 물으면 목록이 늦게 와서 드롭다운이 잠깐 빈 채로 보입니다.
     */
    @GetMapping("/llm-models")
    public LlmModelSettingsResponse llmModels() {
        return LlmModelSettingsResponse.of(llmModelService.current(), llmModelService.available());
    }

    @PutMapping("/llm-models")
    public LlmModelSettingsResponse updateLlmModels(@RequestBody List<UpdateLlmModelRequest> requests) {
        llmModelService.update(requests);
        return LlmModelSettingsResponse.of(llmModelService.current(), llmModelService.available());
    }

    @GetMapping("/settings")
    public List<SystemConfigResponse> settings() {
        return systemConfigService.list();
    }

    @PutMapping("/settings")
    public List<SystemConfigResponse> updateSettings(@RequestBody List<UpdateConfigRequest> requests) {
        return systemConfigService.update(requests);
    }

    // ── 규제 파라미터·규제지역 (설계 I68) ─────────

    /**
     * 기준 스트레스 금리를 한국은행 통계로 다시 산출한다 (설계 I116).
     *
     * <p>월 1회 자동으로 돌지만, 규제가 바뀌었거나 값을 확인하고 싶을 때 손으로 부릅니다.
     * 산출하지 못하면(키 미설정·조회 실패) <b>기존 값을 그대로 둡니다.</b>
     */
    @PostMapping("/stress-rate/refresh")
    public Map<String, Object> refreshStressRate() {
        return stressRateService.refresh()
                .map(d -> Map.<String, Object>of(
                        "refreshed", true,
                        "stressRate", d.stressRate(),
                        "source", d.source()))
                .orElse(Map.of(
                        "refreshed", false,
                        "message", "산출하지 못했습니다. 기존 값을 그대로 씁니다 (키 미설정이거나 조회 실패)"));
    }

    @GetMapping("/regulations")
    public RegulationProfileResponse regulations() {
        return regulationAdminService.profiles();
    }

    @PutMapping("/regulations/params")
    public RegulationProfileResponse updateRegulationParams(
            @RequestBody List<UpdateRegulationParamRequest> requests) {
        return regulationAdminService.updateParams(requests);
    }

    @PostMapping("/regulations/profiles")
    public RegulationProfileResponse createRegulationProfile(
            @RequestBody CreateRegulationProfileRequest request) {
        return regulationAdminService.createProfile(request);
    }

    @PutMapping("/regulations/profiles/{profile}/activate")
    public RegulationProfileResponse activateRegulationProfile(@PathVariable String profile) {
        return regulationAdminService.activateProfile(profile);
    }

    @GetMapping("/regulated-areas")
    public List<RegulatedAreaResponse> regulatedAreas() {
        return regulationAdminService.areas();
    }

    @PostMapping("/regulated-areas")
    @ResponseStatus(HttpStatus.CREATED)
    public List<RegulatedAreaResponse> addRegulatedArea(@RequestBody RegulatedAreaRequest request) {
        return regulationAdminService.addArea(request);
    }

    @DeleteMapping("/regulated-areas/{id}")
    public List<RegulatedAreaResponse> deleteRegulatedArea(@PathVariable Long id) {
        return regulationAdminService.deleteArea(id);
    }

    @GetMapping("/notifications")
    public List<NotificationLogResponse> notifications() {
        return notificationService.recentNotifications();
    }

    /** 알림 스위치 상태 — 읽기 전용 (설계 I215). 배포로 정하는 값이라 여기서 못 바꿉니다. */
    @GetMapping("/notification-settings")
    public NotificationSettingsResponse notificationSettings() {
        return notificationService.notificationSettings();
    }

    /**
     * 그룹 목록 (설계 I89 · 규칙 7·12).
     *
     * <p><b>관리자 경로에만 둡니다.</b> 회원은 다른 그룹이 있는지도 알 수 없어야 하므로
     * `/api/groups` 쪽에는 목록 API가 없습니다.
     */
    @GetMapping("/groups")
    public List<GroupResponse> groups() {
        return groupService.listAll();
    }

    /** 회원을 넣을 그룹을 미리 만든다 (규칙 12). */
    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(@RequestBody GroupRenameRequest request) {
        return groupService.createByAdmin(request.name());
    }
}
