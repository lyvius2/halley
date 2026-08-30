package banghak.home.halley.application.event;

import banghak.home.halley.application.service.LlmRecommendationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 사람의 판단이 바뀌면 AI에게 다시 묻는다 (설계 I78).
 *
 * <p><b>커밋 후에 돕니다.</b> 저장이 끝나기 전에 물으면 방금 쓴 코멘트가 빠진 프롬프트가 나갑니다.
 *
 * <p><b>가상 스레드로 비켜서 돕니다.</b> LLM 응답은 수십 초가 걸리는데 그동안 점수를 저장한
 * 사용자를 붙잡아 둘 이유가 없습니다. 결과가 오기 전까지 화면에는 <b>이전 값이 그대로</b>
 * 보이고(`LlmRecommendationService.find`), 진행 중 표시가 붙습니다(I72).
 */
@Slf4j
@Component
public class PropertyInsightListener {

    private final LlmRecommendationService llmRecommendationService;

    public PropertyInsightListener(LlmRecommendationService llmRecommendationService) {
        this.llmRecommendationService = llmRecommendationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInsightChanged(PropertyInsightChanged event) {
        Thread.ofVirtual().name("llm-insight-" + event.propertyId()).start(() -> {
            try {
                log.info("Re-asking LLM after insight change. propertyId={}, reason={}",
                        event.propertyId(), event.reason());
                // 입력이 그대로면 프롬프트 해시가 같아 다시 부르지 않는다 (설계 I59).
                // 새 추천이 저장되면 그쪽에서 재채점까지 한다 (설계 I84)
                llmRecommendationService.ensureRecommendation(event.propertyId());
            } catch (RuntimeException e) {
                // 여기서 새어 나가면 가상 스레드가 조용히 죽어 아무 기록도 남지 않는다
                log.error("Failed to re-ask LLM after insight change. propertyId={}, reason={}, cause={}",
                        event.propertyId(), event.reason(), e.toString(), e);
            }
        });
    }
}
