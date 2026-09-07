package banghak.home.halley.config;

import banghak.home.halley.application.event.PropertyCreatedEvent;
import banghak.home.halley.application.event.PropertyDeletedEvent;
import banghak.home.halley.application.service.NotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 매물 등록·삭제 알림. */
@Component
public class PropertyCreatedListener {

    private final NotificationService notificationService;

    public PropertyCreatedListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPropertyCreated(PropertyCreatedEvent event) {
        notificationService.sendPropertyCreated(event.propertyId());
    }

 /** 매물 삭제 알림. 함께 보던 사람은 왜 사라졌는지 알아야 한다. */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPropertyDeleted(PropertyDeletedEvent event) {
        notificationService.sendPropertyDeleted(event.groupId(), event.propertyName());
    }
}
