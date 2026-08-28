package banghak.home.halley.config;

import banghak.home.halley.application.event.PropertyCreatedEvent;
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

    /** 초등학교·실거래가 보정 (설계 I53). 알림과 독립적으로 돌게 따로 둔다. */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPropertyCreatedForEnrichment(PropertyCreatedEvent event) {
        propertyEnrichmentService.enrich(event.propertyId());
    }
}
