package banghak.home.halley.domain.itinerary;

import java.time.Instant;

/**
 * 그 매물에 가 봤다 (설계 I197).
 *
 * <p>전에는 <b>계획에 딸린 값</b>이었습니다(`visit_plan_stop.visited`). 그래서
 * 계획을 지우면 같이 사라졌고, 같은 매물을 다음 임장에 또 넣으면 처음 보는 것처럼
 * 되돌아왔습니다.
 *
 * <p>가 봤다는 것은 <b>매물에 대한 사실</b>입니다. 어느 계획으로 갔는지와 무관합니다.
 *
 * <p><b>사람마다 다릅니다.</b> 같은 그룹이라도 A가 간 곳을 B가 간 것은 아닙니다.
 *
 * @param visitedAt 체크한 시각. 언제 갔는지 <b>기록으로 남습니다</b>
 */
public record PropertyVisit(
        Long id,
        Long propertyId,
        Long userId,
        Instant visitedAt
) {
}
