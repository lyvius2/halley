package banghak.home.halley.config;

import banghak.home.halley.application.event.WorkplacesChangedEvent;
import banghak.home.halley.application.service.LlmRecommendationService;
import banghak.home.halley.application.service.ScoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 직장 위치가 바뀌면 AI 추천도를 다시 뽑는다. */
@Slf4j
@Component
public class WorkplacesChangedListener {

    private final LlmRecommendationService llmRecommendationService;
    private final ScoringService scoringService;

    public WorkplacesChangedListener(LlmRecommendationService llmRecommendationService,
                                     ScoringService scoringService) {
        this.llmRecommendationService = llmRecommendationService;
        this.scoringService = scoringService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkplacesChanged(WorkplacesChangedEvent event) {
        try {
            final int refreshed = llmRecommendationService.refreshForWorkplaceChange();
            log.info("AI recommendation refresh triggered. cause={}, refreshed={}", event.cause(), refreshed);
            if (refreshed > 0) {
                scoringService.rescoreAll();
            }
        } catch (RuntimeException e) {
            log.warn("AI recommendation refresh failed. cause={}, error={}", event.cause(), e.getMessage());
        }
    }
}
