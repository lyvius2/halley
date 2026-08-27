package banghak.home.halley.batch;

import banghak.home.halley.application.service.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationRetryJob {

    private final NotificationService notificationService;

    public NotificationRetryJob(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void run() {
        notificationService.resendRetrying();
    }
}
