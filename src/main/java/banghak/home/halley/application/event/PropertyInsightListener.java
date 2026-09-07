package banghak.home.halley.application.event;

import banghak.home.halley.application.service.LlmRecommendationService;
import banghak.home.halley.application.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 사람의 판단이 바뀌면 AI에게 다시 묻는다. */
@Slf4j
@Component
public class PropertyInsightListener {

    private final LlmRecommendationService llmRecommendationService;
    private final NotificationService notificationService;

    public PropertyInsightListener(LlmRecommendationService llmRecommendationService,
                                   NotificationService notificationService) {
        this.llmRecommendationService = llmRecommendationService;
        this.notificationService = notificationService;
    }

 /** 그룹 웹훅이 있으면 알린다. 없으면 조용히 지나간다. */
    private void notify(PropertyInsightChanged event) {
        try {
            switch (event.kind()) {
                case COMMENT -> notificationService.sendCommentCreated(
                        event.propertyId(), event.actorNickname(), event.detail());
                case COMFORT_SCORE -> notificationService.sendComfortScored(
                        event.propertyId(), event.actorNickname(), scoreOf(event.detail()));
                case EDIT -> { }
            }
        } catch (RuntimeException e) {
            log.warn("Notification failed. propertyId={}, kind={}, cause={}",
                    event.propertyId(), event.kind(), e.toString());
        }
    }

 /** 점수를 못 읽으면 null. 없는 점수를 지어내느니 "평가했습니다"까지만 말한다. */
    private static Integer scoreOf(String detail) {
        try {
            return detail == null ? null : Integer.valueOf(detail.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInsightChanged(PropertyInsightChanged event) {
        Thread.ofVirtual().name("llm-insight-" + event.propertyId()).start(() -> {
            try {
                log.info("Re-asking LLM after insight change. propertyId={}, reason={}",
                        event.propertyId(), event.reason());
                notify(event);
                llmRecommendationService.ensureRecommendation(event.propertyId());
            } catch (RuntimeException e) {
                log.error("Failed to re-ask LLM after insight change. propertyId={}, reason={}, cause={}",
                        event.propertyId(), event.reason(), e.toString(), e);
            }
        });
    }
}
