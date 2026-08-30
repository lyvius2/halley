package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.CreateRegulationProfileRequest;
import banghak.home.halley.adapter.inbound.web.dto.NotificationLogResponse;
import banghak.home.halley.adapter.inbound.web.dto.RegulatedAreaRequest;
import banghak.home.halley.adapter.inbound.web.dto.RegulatedAreaResponse;
import banghak.home.halley.adapter.inbound.web.dto.RegulationProfileResponse;
import banghak.home.halley.adapter.inbound.web.dto.SystemConfigResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateConfigRequest;
import banghak.home.halley.adapter.inbound.web.dto.UpdateRegulationParamRequest;
import banghak.home.halley.application.service.NotificationService;
import banghak.home.halley.application.service.RegulationAdminService;
import banghak.home.halley.application.service.SystemConfigService;
import banghak.home.halley.batch.ListingCheckJob;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import banghak.home.halley.adapter.inbound.web.dto.GroupRenameRequest;
import banghak.home.halley.adapter.inbound.web.dto.GroupResponse;
import banghak.home.halley.application.service.GroupService;
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
    private final ListingCheckJob listingCheckJob;
    private final RegulationAdminService regulationAdminService;
    private final GroupService groupService;

    public AdminSettingsController(SystemConfigService systemConfigService,
                                   NotificationService notificationService,
                                   ListingCheckJob listingCheckJob,
                                   RegulationAdminService regulationAdminService,
                                   GroupService groupService) {
        this.systemConfigService = systemConfigService;
        this.notificationService = notificationService;
        this.listingCheckJob = listingCheckJob;
        this.regulationAdminService = regulationAdminService;
        this.groupService = groupService;
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

    @PostMapping("/listing-check/run")
    public Map<String, Object> runListingCheck() {
        listingCheckJob.run();
        return Map.of("done", true);
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
