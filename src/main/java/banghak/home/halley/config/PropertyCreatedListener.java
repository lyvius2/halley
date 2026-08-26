package banghak.home.halley.config;

import banghak.home.halley.application.event.PropertyCreatedEvent;
import banghak.home.halley.application.service.NotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PropertyCreatedListener {

    private final NotificationService notificationService;

    public PropertyCreatedListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPropertyCreated(PropertyCreatedEvent event) {
        notificationService.sendPropertyCreated(event.propertyId());
    }
}
