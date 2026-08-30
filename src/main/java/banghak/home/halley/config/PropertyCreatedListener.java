package banghak.home.halley.config;

import banghak.home.halley.application.event.PropertyCreatedEvent;
import banghak.home.halley.application.event.PropertyDeletedEvent;
import banghak.home.halley.application.service.NotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 매물 등록·삭제 알림.
 *
 * <p><b>보정은 여기 없습니다.</b> 등록 요청이 앞 단계를 기다렸다가 직접 부릅니다 (설계 I110) —
 * 커밋 직후 이벤트로 띄우면 요청이 기다리는 앞 단계와 겹쳐 돌아, AI가 채점 전 상태를 봅니다.
 */
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

    /** 매물 삭제 알림 (설계 I96). 함께 보던 사람은 왜 사라졌는지 알아야 한다. */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPropertyDeleted(PropertyDeletedEvent event) {
        notificationService.sendPropertyDeleted(event.groupId(), event.propertyName());
    }
}
