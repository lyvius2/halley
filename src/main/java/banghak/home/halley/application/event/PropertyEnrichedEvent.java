package banghak.home.halley.application.event;

/**
 * 등록 후 보정이 <b>끝났다</b> (설계 I126).
 *
 * <p><b>`PropertyCreatedEvent`가 아닙니다.</b> 커밋 직후에 띄우면 요청이 기다리는 앞 단계와
 * 겹쳐 돌고(I110), 공시가격이 없는 상태로 지표를 계산하며, 같은 국토부 API를 뒤 단계와
 * 동시에 두드립니다.
 *
 * <p>보정보다 <b>더 오래 걸리는 후속 작업</b>을 여기에 붙입니다 — 지금은 가격 전망 하나입니다.
 */
public record PropertyEnrichedEvent(Long propertyId) {
}
