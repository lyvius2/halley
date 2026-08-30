package banghak.home.halley.config;

import banghak.home.halley.application.event.PropertyCreatedEvent;
import banghak.home.halley.application.event.PropertyDeletedEvent;
import banghak.home.halley.application.service.NotificationService;
import banghak.home.halley.application.service.PropertyEnrichmentService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PropertyCreatedListener {

    private final NotificationService notificationService;
    private final PropertyEnrichmentService propertyEnrichmentService;

    public PropertyCreatedListener(NotificationService notificationService,
                                   PropertyEnrichmentService propertyEnrichmentService) {
        this.notificationService = notificationService;
        this.propertyEnrichmentService = propertyEnrichmentService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPropertyCreated(PropertyCreatedEvent event) {
        notificationService.sendPropertyCreated(event.propertyId());
    }

    /** 매물 삭제 알림 (설계 I96). 함께 보던 사람은 왜 사라졌는지 알아야 한다. */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPropertyDeleted(PropertyDeletedEvent event) {
        notificationService.sendPropertyDeleted(event.groupId(), event.propertyName());
    }

    /** 초등학교·실거래가 보정 (설계 I53). 알림과 독립적으로 돌게 따로 둔다. */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPropertyCreatedForEnrichment(PropertyCreatedEvent event) {
        propertyEnrichmentService.enrich(event.propertyId());
    }
}
