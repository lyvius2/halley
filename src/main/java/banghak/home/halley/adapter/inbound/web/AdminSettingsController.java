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

    public AdminSettingsController(SystemConfigService systemConfigService,
                                   NotificationService notificationService,
                                   ListingCheckJob listingCheckJob,
                                   RegulationAdminService regulationAdminService) {
        this.systemConfigService = systemConfigService;
        this.notificationService = notificationService;
        this.listingCheckJob = listingCheckJob;
        this.regulationAdminService = regulationAdminService;
    }

    @GetMapping("/settings")
    public List<SystemConfigResponse> settings() {
        return systemConfigService.list();
    }

    @PutMapping("/settings")
    public List<SystemConfigResponse> updateSettings(@RequestBody List<UpdateConfigRequest> requests) {
        return systemConfigService.update(requests);
    }

    @PostMapping("/settings/slack/test")
    public Map<String, Object> testSlack() {
        return Map.of("sent", notificationService.testSend());
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
}
