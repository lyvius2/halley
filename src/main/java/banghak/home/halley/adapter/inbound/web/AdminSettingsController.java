package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.NotificationLogResponse;
import banghak.home.halley.adapter.inbound.web.dto.SystemConfigResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateConfigRequest;
import banghak.home.halley.application.service.NotificationService;
import banghak.home.halley.application.service.SystemConfigService;
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

    public AdminSettingsController(SystemConfigService systemConfigService,
                                   NotificationService notificationService) {
        this.systemConfigService = systemConfigService;
        this.notificationService = notificationService;
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
}
