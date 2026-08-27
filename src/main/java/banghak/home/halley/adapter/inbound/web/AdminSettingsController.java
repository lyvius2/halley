package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.NotificationLogResponse;
import banghak.home.halley.adapter.inbound.web.dto.SystemConfigResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateConfigRequest;
import banghak.home.halley.application.service.NotificationService;
import banghak.home.halley.application.service.SystemConfigService;
import banghak.home.halley.batch.ListingCheckJob;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminSettingsController {

    private final SystemConfigService systemConfigService;
    private final NotificationService notificationService;
    private final ListingCheckJob listingCheckJob;

    public AdminSettingsController(SystemConfigService systemConfigService,
                                   NotificationService notificationService,
                                   ListingCheckJob listingCheckJob) {
        this.systemConfigService = systemConfigService;
        this.notificationService = notificationService;
        this.listingCheckJob = listingCheckJob;
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
