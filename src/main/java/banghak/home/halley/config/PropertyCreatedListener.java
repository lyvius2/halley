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
 *
 * <p><b>`fallbackExecution = true` 가 반드시 있어야 합니다 (설계 I216).</b>
 * 이게 없으면 <b>트랜잭션 밖에서 띄운 이벤트가 조용히 버려집니다</b> — 예외도 로그도
 * 없습니다. `PropertyService.create` 에 트랜잭션이 없어서(설계 I216) 등록·삭제 알림이
 * 한참 동안 <b>아무 흔적 없이 안 나가고 있었습니다.</b>
 *
 * <p>매물은 이벤트를 띄우기 <b>전에 이미 저장돼 있습니다</b>(리포지터리가 바로 커밋합니다).
 * 되돌릴 것이 없으므로 커밋을 기다릴 이유도 없습니다.
 */
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

    /** 매물 삭제 알림 (설계 I96). 함께 보던 사람은 왜 사라졌는지 알아야 한다. */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPropertyDeleted(PropertyDeletedEvent event) {
        notificationService.sendPropertyDeleted(event.groupId(), event.propertyName());
    }
}
